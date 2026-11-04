package skincare.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;

/** Public directory only; neither live availability nor a clinical recommendation. */
@Component
public class NmcMedicalClient implements PublicMedicalClient {
    private static final int LIMIT = 2 * 1024 * 1024;
    private final String key;
    private final java.util.concurrent.Semaphore slots=new java.util.concurrent.Semaphore(4);
    public NmcMedicalClient(@Value("${skincare.medical.service-key:}") String key) { this.key = key.strip(); }
    @Override public Result search(Query q) {
        if (key.isBlank()) return failure(q,"UNCONFIGURED");
        if(!slots.tryAcquire())return failure(q,"RATE_LIMITED");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) requestUri(q).toURL().openConnection();
            connection.setConnectTimeout(3000); connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false); connection.setRequestProperty("Accept","application/xml");
            int code=connection.getResponseCode();
            if(code==401 || code==403) return failure(q,"AUTH_ERROR");
            if(code==429) return failure(q,"RATE_LIMITED");
            if(code!=200) return failure(q,"UNAVAILABLE");
            try(var in=connection.getInputStream()) {
                byte[] bytes=in.readNBytes(LIMIT+1);
                if(bytes.length>LIMIT)return failure(q,"INVALID_RESPONSE");
                return parse(q,bytes);
            }
        } catch(Exception ignored) { // Do not log the URL, key, response body or search location.
            return failure(q,"UNAVAILABLE");
        } finally { if(connection!=null)connection.disconnect(); slots.release(); }
    }
    protected URI requestUri(Query q) {
        String path=q.kind()==Kind.HOSPITAL ? "HsptlAsembySearchService/getHsptlMdcncListInfoInqire" : "ErmctInsttInfoInqireService/getParmacyListInfoInqire";
        var params=new LinkedHashMap<String,String>();params.put("serviceKey",key);
        if(q.nearby()) {
            path=q.kind()==Kind.HOSPITAL ? "HsptlAsembySearchService/getHsptlMdcncLcinfoInqire" : "ErmctInsttInfoInqireService/getParmacyLcinfoInqire";
            params.put("WGS84_LAT",String.valueOf(q.latitude()));params.put("WGS84_LON",String.valueOf(q.longitude()));
        } else {
            params.put("Q0",q.sido());params.put("Q1",q.sigungu());params.put("QN",q.name());params.put("ORD","NAME");
        }
        params.put("pageNo",String.valueOf(q.page()+1));params.put("numOfRows",String.valueOf(q.size()));
        String query=params.entrySet().stream().filter(e->e.getValue()!=null&&!e.getValue().isBlank())
            .map(e->e.getKey()+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        return URI.create("https://apis.data.go.kr/B552657/"+path+"?"+query);
    }
    static String source(Query q) { return "https://www.data.go.kr/data/"+(q.kind()==Kind.HOSPITAL?"15000736":"15000576")+"/openapi.do"; }
    static Result failure(Query q,String status) { return new Result(status,List.of(),q.page(),q.size(),false,null,source(q),Instant.now()); }
    static Result parse(Query q,byte[] bytes) {
        try {
            var factory=DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            var builder=factory.newDocumentBuilder();builder.setErrorHandler(new DefaultHandler());
            var root=builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            String code=text(root,"resultCode");if(code==null)code=text(root,"returnReasonCode");
            if(Set.of("20","21","30","31","32").contains(Objects.toString(code,"")))return failure(q,"AUTH_ERROR");
            if(Set.of("22","23").contains(Objects.toString(code,"")))return failure(q,"RATE_LIMITED");
            if(!"00".equals(code))return failure(q,"INVALID_RESPONSE");
            long total=Long.parseLong(text(root,"totalCount"));if(total<0)throw new IllegalArgumentException();
            var nodes=root.getElementsByTagName("item");var items=new ArrayList<Place>();var ids=new HashSet<String>();
            if(nodes.getLength()>q.size())throw new IllegalArgumentException();
            for(int i=0;i<nodes.getLength();i++) {
                var e=(Element)nodes.item(i);String id=text(e,"hpid"),name=text(e,"dutyName"),address=text(e,"dutyAddr");
                if(id==null||name==null||address==null||!ids.add(id))throw new IllegalArgumentException();
                String tel=text(e,"dutyTel1");if(tel!=null&&!tel.matches("[+0-9() -]{5,30}"))tel=null;
                String emergency=text(e,"dutyEryn");
                items.add(new Place(q.kind()+":"+id,name,address,tel,text(e,q.nearby()?"dutyDivName":"dutyDivNam"),
                    coordinate(e,q.nearby()?"latitude":"wgs84Lat",90),coordinate(e,q.nearby()?"longitude":"wgs84Lon",180),"1".equals(emergency)?Boolean.TRUE:"2".equals(emergency)?Boolean.FALSE:null));
            }
            if(total>0 && items.isEmpty() && (long)q.page()*q.size()<total)throw new IllegalArgumentException();
            return new Result("OK",items,q.page(),q.size(),(long)(q.page()+1)*q.size()<total,total,source(q),Instant.now());
        } catch(Exception invalid) { return failure(q,"INVALID_RESPONSE"); }
    }
    static String text(Element e,String tag) {
        var node=e.getElementsByTagName(tag).item(0); if(node==null)return null;
        String value=node.getTextContent().strip();if(value.length()>1000)throw new IllegalArgumentException();
        return value.isEmpty()?null:value;
    }
    static Double coordinate(Element e,String tag,int bound) {
        try {double v=Double.parseDouble(text(e,tag));return Double.isFinite(v)&&v!=0&&Math.abs(v)<=bound?v:null;}
        catch(Exception ignored){return null;}
    }
}
