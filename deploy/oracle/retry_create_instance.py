"""Keep trying to create the Always Free Ampere VM until Oracle has capacity.

"Out of capacity for shape VM.Standard.A1.Flex" is common on free accounts.
This script retries every --interval seconds and stops at the first success.

Safety:
  * Requests stay inside the Always Free limits (A1.Flex, <= 2 OCPU / 12 GB in
    total, boot volume <= 200 GB at 10 VPU). Larger values are refused.
  * Before every attempt it checks for an existing non-terminated instance with
    the same name and stops if one exists, so it never creates duplicates.
  * --dry-run checks the config, network, image and key, then exits.

Run with Docker on Windows (no local install needed), e.g.:
  docker run --rm -it -v C:\\Users\\me\\.oci:/oci:ro -v C:\\Users\\me\\.ssh:/ssh:ro \
      -v <repo>\\deploy\\oracle:/work:ro python:3.12-slim \
      sh -c "pip install -q oci && python /work/retry_create_instance.py \
             --ssh-public-key /ssh/oracle-skincare.key.pub --dry-run"
"""
from __future__ import annotations

import argparse
import datetime
import json
import os
import sys
import time

import oci

SHAPE = "VM.Standard.A1.Flex"
# Always Free A1 allowance since June 2026 (was 4 OCPU / 24 GB):
# docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
FREE_OCPUS, FREE_MEMORY_GB, FREE_STORAGE_GB = 2, 12, 200
ACTIVE = {"PROVISIONING", "STARTING", "RUNNING", "STOPPING", "STOPPED", "MOVING", "CREATING_IMAGE"}


def log(message: str) -> None:
    print(f"[{datetime.datetime.now():%m-%d %H:%M:%S}] {message}", flush=True)


def load_config(path: str, profile: str) -> dict:
    # A config written on Windows points at C:\...\key.pem, which does not exist
    # inside the container. Rewrite key_file to the same file name next to the
    # config (in a private temp copy; the original stays untouched).
    lines = []
    with open(path, encoding="utf-8-sig") as handle:
        for line in handle:
            name, sep, value = line.partition("=")
            if sep and name.strip() == "key_file":
                key = value.split("#", 1)[0].strip()
                if not os.path.exists(os.path.expanduser(key)):
                    key = os.path.join(os.path.dirname(path), os.path.basename(key.replace("\\", "/")))
                line = f"key_file={key}\n"
            lines.append(line)
    fixed = "/tmp/oci-config" if os.path.isdir("/tmp") else path + ".resolved"
    with os.fdopen(os.open(fixed, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w", encoding="utf-8") as out:
        out.writelines(lines)
    config = oci.config.from_file(fixed, profile)
    oci.config.validate_config(config)
    return config


def parse_shapes(text: str) -> list[tuple[int, int]]:
    shapes = []
    for item in text.split(","):
        ocpus, memory = (int(v) for v in item.split(":"))
        if not (1 <= ocpus <= FREE_OCPUS and 1 <= memory <= FREE_MEMORY_GB):
            raise SystemExit(f"{item} is outside the Always Free limit ({FREE_OCPUS} OCPU / {FREE_MEMORY_GB} GB)")
        shapes.append((ocpus, memory))
    return shapes


def find_one(items, name: str, kind: str):
    matches = [item for item in items if item.display_name == name]
    if len(matches) != 1:
        found = ", ".join(sorted(i.display_name for i in items)) or "none"
        raise SystemExit(f"{kind} '{name}' not found exactly once (found: {found})")
    return matches[0]


def active_instances(compute, compartment: str) -> list:
    instances = oci.pagination.list_call_get_all_results(compute.list_instances, compartment).data
    return [i for i in instances if i.lifecycle_state in ACTIVE]


def public_ip(compute, network, compartment: str, instance_id: str) -> str:
    for attachment in compute.list_vnic_attachments(compartment, instance_id=instance_id).data:
        if attachment.lifecycle_state == "ATTACHED":
            vnic = network.get_vnic(attachment.vnic_id).data
            if vnic.is_primary:
                return vnic.public_ip or "(none)"
    return "(unknown)"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--config", default="/oci/config")
    parser.add_argument("--profile", default="DEFAULT")
    parser.add_argument("--compartment", help="defaults to the tenancy (root) compartment")
    parser.add_argument("--name", default="skincare-server")
    parser.add_argument("--vcn", default="skincare-vcn")
    parser.add_argument("--subnet", default="public subnet-skincare-vcn")
    parser.add_argument("--ssh-public-key", required=True)
    parser.add_argument("--shapes", default="2:12",
                        help="OCPU:GB tried in order each round (smaller ones can be resized up later)")
    parser.add_argument("--boot-gb", type=int, default=100)
    parser.add_argument("--interval", type=int, default=120, help="seconds between rounds")
    parser.add_argument("--max-hours", type=float, default=72)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    shapes = parse_shapes(args.shapes)
    if not 50 <= args.boot_gb <= FREE_STORAGE_GB:
        raise SystemExit(f"--boot-gb must be 50..{FREE_STORAGE_GB} to stay free")
    config = load_config(args.config, args.profile)
    compartment = args.compartment or config["tenancy"]
    identity = oci.identity.IdentityClient(config)
    compute = oci.core.ComputeClient(config)
    network = oci.core.VirtualNetworkClient(config)

    log(f"region {config['region']}, user OK")
    ads = [ad.name for ad in identity.list_availability_domains(compartment).data]
    vcn = find_one(network.list_vcns(compartment).data, args.vcn, "VCN")
    subnet = find_one(network.list_subnets(compartment, vcn_id=vcn.id).data, args.subnet, "Subnet")
    if subnet.prohibit_public_ip_on_vnic:
        raise SystemExit(f"Subnet '{args.subnet}' is private; choose the public subnet")
    images = [i for i in compute.list_images(
        compartment, operating_system="Canonical Ubuntu", operating_system_version="24.04",
        shape=SHAPE, sort_by="TIMECREATED", sort_order="DESC").data if "Minimal" not in i.display_name]
    if not images:
        raise SystemExit("No Ubuntu 24.04 image for A1.Flex in this region")
    image = images[0]
    with open(args.ssh_public_key, encoding="utf-8") as handle:
        ssh_key = handle.read().strip()
    if not ssh_key.startswith(("ssh-rsa ", "ssh-ed25519 ", "ecdsa-")):
        raise SystemExit("--ssh-public-key must be the .pub file, not the private key")

    used = active_instances(compute, compartment)
    used_ocpus = sum((i.shape_config.ocpus or 0) for i in used if i.shape == SHAPE)
    log(f"ADs {ads}; subnet {subnet.display_name}; image {image.display_name}")
    log(f"existing A1 OCPUs in use: {used_ocpus:g}; shapes to try: {shapes}; boot {args.boot_gb} GB @ 10 VPU")
    if any(i.display_name == args.name for i in used):
        log(f"'{args.name}' already exists - nothing to do")
        return
    if args.dry_run:
        log("dry run OK - remove --dry-run to start retrying")
        return

    # One launch request per round: free accounts throttle (HTTP 429) quickly,
    # so shapes and ADs are rotated across rounds instead of tried back-to-back.
    plan = [(o, m, ad) for o, m in shapes if used_ocpus + o <= FREE_OCPUS for ad in ads]
    if not plan:
        raise SystemExit("no requested shape fits in the remaining Always Free OCPUs")
    deadline = time.monotonic() + args.max_hours * 3600
    attempt, wait = 0, args.interval
    while time.monotonic() < deadline:
        ocpus, memory, ad = plan[attempt % len(plan)]
        try:
            if any(i.display_name == args.name for i in active_instances(compute, compartment)):
                log(f"'{args.name}' now exists - stopping without creating another")
                return
            attempt += 1
            details = oci.core.models.LaunchInstanceDetails(
                availability_domain=ad, compartment_id=compartment, display_name=args.name, shape=SHAPE,
                shape_config=oci.core.models.LaunchInstanceShapeConfigDetails(ocpus=ocpus, memory_in_gbs=memory),
                source_details=oci.core.models.InstanceSourceViaImageDetails(
                    image_id=image.id, boot_volume_size_in_gbs=args.boot_gb, boot_volume_vpus_per_gb=10),
                create_vnic_details=oci.core.models.CreateVnicDetails(subnet_id=subnet.id, assign_public_ip=True),
                metadata={"ssh_authorized_keys": ssh_key})
            instance = compute.launch_instance(details).data
        except oci.exceptions.ServiceError as error:
            if error.status == 429:
                wait = min(max(wait, args.interval) * 2, 600)
                log(f"#{attempt} rate limited - pausing {wait}s")
            elif error.status == 500 or "capacity" in str(error.message).lower():
                wait = args.interval
                log(f"#{attempt} {ocpus} OCPU/{memory} GB: out of capacity - next try in {wait}s")
            else:
                raise SystemExit(f"stopping on unexpected error: {error.status} {error.code}: {error.message}")
            time.sleep(wait)
            continue
        log(f"#{attempt} CREATED {ocpus} OCPU/{memory} GB - waiting for RUNNING...")
        oci.wait_until(compute, compute.get_instance(instance.id), "lifecycle_state", "RUNNING",
                       max_wait_seconds=900)
        ip = public_ip(compute, network, compartment, instance.id)
        log(f"SUCCESS  instance={args.name}  shape={ocpus} OCPU/{memory} GB  public IP={ip}")
        print(json.dumps({"name": args.name, "ocpus": ocpus, "memoryGb": memory, "publicIp": ip}), flush=True)
        return
    raise SystemExit(f"gave up after {args.max_hours} hours")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log("stopped by user")
        sys.exit(130)
