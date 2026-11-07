package skincare.service;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuration of public documents, not a legal or clinical approval. */
@Component
public class PublishedPolicies {
    public record Document(String version,String url) {}
    public record Previews(String version,String terms,String privacy,String healthData) {}
    public record Manifest(boolean requiredAvailable,Document terms,Document privacy,Document healthData,
                           Document training,boolean trainingAvailable,boolean trainingExportEnabled,Previews previews,Document location) {}
    private final String terms,privacy,training;
    public PublishedPolicies(String terms,String privacy,String training) {this(terms,privacy,training,false,"");}
    @org.springframework.beans.factory.annotation.Autowired
    public PublishedPolicies(@Value("${skincare.consent.terms-policy-url:}")String terms,
        @Value("${skincare.consent.privacy-policy-url:}")String privacy,
        @Value("${skincare.consent.training-policy-url:}")String training,
        @Value("${skincare.consent.builtin-policies:false}")boolean builtin,
        @Value("${social.public-base-url:http://127.0.0.1:9800}")String base) {
        String origin=builtin?policyOrigin(base):null;
        this.terms=origin!=null?origin+"/api/wc/policies/"+ConsentPolicy.VERSION+"/terms":publicHttps(terms);
        this.privacy=origin!=null?origin+"/api/wc/policies/"+ConsentPolicy.VERSION+"/privacy":publicHttps(privacy);
        this.training=origin!=null?origin+"/api/wc/policies/2026-11-04/training":publicHttps(training);
    }
    public static String policyOrigin(String value) {
        try {
            var u=URI.create(value.strip());
            boolean loopback="http".equals(u.getScheme()) && java.util.Set.of("localhost","127.0.0.1","[::1]").contains(u.getHost());
            if((!loopback && !"https".equals(u.getScheme())) || u.getHost()==null || u.getUserInfo()!=null || u.getPort()==0 || u.getPort()>65535
                || u.getQuery()!=null || u.getFragment()!=null || (u.getPath()!=null && !u.getPath().isEmpty() && !u.getPath().equals("/")))return null;
            return u.getScheme()+"://"+u.getRawAuthority();
        }catch(RuntimeException e){return null;}
    }
    public static String publicHttps(String value) {
        if(value==null||value.isBlank()||value.strip().length()>1000)return null;
        try {
            var uri=URI.create(value.strip());
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getPort()>65535||uri.getPort()==0)return null;
            if(uri.getPath()!=null && uri.getPath().startsWith("/api/wc/policy-previews/"))return null;
            return uri.toASCIIString();
        }catch(IllegalArgumentException e){return null;}
    }
    public boolean requiredAvailable(){return terms!=null&&privacy!=null;}
    public String trainingUrl(){return training;}
    public Manifest manifest(){return new Manifest(requiredAvailable(),new Document(ConsentPolicy.VERSION,terms),
        new Document(ConsentPolicy.VERSION,privacy),new Document(ConsentPolicy.HEALTH_VERSION,privacy==null?null:privacy.split("#",2)[0]+"#health-data"),
        new Document(ConsentPolicy.TRAINING_VERSION,training),training!=null,false,
        new Previews("draft-2026-11-01.1","/api/wc/policy-previews/terms","/api/wc/policy-previews/privacy","/api/wc/policy-previews/privacy#health-data"),new Document(ConsentPolicy.LOCATION_VERSION,"/api/wc/policies/2026-11-04/location"));}
}
