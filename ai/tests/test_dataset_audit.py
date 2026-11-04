from pathlib import Path
from PIL import Image
from audit_dataset import audit


def dataset(root):
    for split_index, split in enumerate(["train", "valid", "test"]):
        for label_index, label in enumerate(["abrasion", "bruise", "burn", "laceration", "normal skin"]):
            path = root / split / label / "sample.png"
            path.parent.mkdir(parents=True)
            Image.new("RGB", (4, 4), (split_index * 70, label_index * 40, 10)).save(path)


def test_clean_dataset_has_no_cross_split_pixel_duplicates(tmp_path):
    dataset(tmp_path)
    result = audit(tmp_path)
    assert result["pixel_duplicate_cross_split_groups"] == 0
    assert result["conflicting_pixel_label_groups"] == 0


def test_duplicate_with_different_encoding_is_detected(tmp_path):
    dataset(tmp_path)
    # Same pixels in another file format/name must still be treated as leakage.
    with Image.open(tmp_path / "train/abrasion/sample.png") as image:
        image.save(tmp_path / "test/abrasion/renamed.PNG")
    assert audit(tmp_path)["pixel_duplicate_cross_split_groups"] == 1


def test_conflicting_labels_are_detected(tmp_path):
    dataset(tmp_path)
    with Image.open(tmp_path / "train/abrasion/sample.png") as image:
        image.save(tmp_path / "train/bruise/conflict.png")
    assert audit(tmp_path)["conflicting_pixel_label_groups"] == 1
