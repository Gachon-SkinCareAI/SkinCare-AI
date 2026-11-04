package skincare.dto;

import lombok.Data;

@Data
public class ConsentRequestDto {
    private boolean agreedHealthData;
    private String healthDataVersion;
    private boolean agreedTerms;
    private boolean agreedPrivacy;
    private String termsVersion;
    private String privacyVersion;
}
