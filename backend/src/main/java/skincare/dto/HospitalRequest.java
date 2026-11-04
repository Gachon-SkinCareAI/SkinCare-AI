package skincare.dto;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import java.time.LocalDate;
public record HospitalRequest(String name,HospitalType type,String region,String address,String tel,
    Boolean nightCare,Boolean emergency,String dataSource,String sourceUrl,LocalDate verifiedOn,String status,Long expectedVersion,String clientRequestId) {}
