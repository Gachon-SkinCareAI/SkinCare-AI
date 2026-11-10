package skincare.external;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class NmcMedicalClientTests {
    @Test void nearbyUsesProviderCoordinateOperationsAndTheirActualFieldNames(){
        for(var kind:PublicMedicalClient.Kind.values()) {
            var nearby=new PublicMedicalClient.Query(kind,"","","",0,20,37.5665,126.978);
            String uri=new NmcMedicalClient("unused").requestUri(nearby).toString();
            assertTrue(uri.contains(kind==PublicMedicalClient.Kind.HOSPITAL?"getHsptlMdcncLcinfoInqire":"getParmacyLcinfoInqire"));
            assertTrue(uri.contains("WGS84_LAT=37.5665"));assertTrue(uri.contains("WGS84_LON=126.978"));assertFalse(uri.contains("ORD="));
            var result=NmcMedicalClient.parse(nearby,wrap("<totalCount>1</totalCount><items><item><hpid>qa</hpid><dutyName>QA</dutyName><dutyAddr>QA</dutyAddr><dutyDivName>category</dutyDivName><latitude>37.56</latitude><longitude>126.97</longitude></item></items>").getBytes(StandardCharsets.UTF_8));
            assertEquals("OK",result.status());assertEquals(37.56,result.items().get(0).latitude());assertEquals(126.97,result.items().get(0).longitude());assertEquals("category",result.items().get(0).category());
        }
    }
    
    final PublicMedicalClient.Query q=new PublicMedicalClient.Query(PublicMedicalClient.Kind.HOSPITAL,"서울특별시","강남구","A & B",0,20);
    PublicMedicalClient.Result parse(String xml){return NmcMedicalClient.parse(q,xml.getBytes(StandardCharsets.UTF_8));}
    String wrap(String body){return "<response><header><resultCode>00</resultCode></header><body>"+body+"</body></response>";}
    @Test void absentKeyIsUnavailableNotAnEmptySuccessfulDirectory(){var r=new NmcMedicalClient("").search(q);assertEquals("UNCONFIGURED",r.status());assertNull(r.total());assertTrue(r.items().isEmpty());}
    @Test void providerPathsPagingAndDecodedKeyAreEncodedExactlyOnce(){
        var c=new NmcMedicalClient("abc+/=");String uri=c.requestUri(q).toASCIIString();
        assertTrue(uri.contains("HsptlAsembySearchService/getHsptlMdcncListInfoInqire"));assertTrue(uri.contains("serviceKey=abc%2B%2F%3D"));assertTrue(uri.contains("Q0="));assertTrue(uri.contains("QN=A+%26+B"));assertTrue(uri.contains("pageNo=1"));
        assertTrue(c.requestUri(new PublicMedicalClient.Query(PublicMedicalClient.Kind.PHARMACY,"","","",2,10)).toASCIIString().contains("getParmacyListInfoInqire"));
    }

    // data.go.kr은 같은 키를 Encoding(%2B %2F %3D)과 Decoding(+ / =) 두 형태로 준다. 어느 쪽을 .env에 넣어도
    // 같은 URL이 만들어져야 하며, 인코딩된 키를 다시 인코딩(%252F)하면 포털이 코드 30으로 거부한다.
    @Test void encodedAndDecodedKeysProduceTheSameSingleEncodedRequest(){
        String decoded="Ab+c/d=="; String encoded="Ab%2Bc%2Fd%3D%3D";
        for(String form:new String[]{decoded,encoded,"\""+encoded+"\"","  "+decoded+"&"}) {
            String uri=new NmcMedicalClient(form).requestUri(q).toASCIIString();
            assertTrue(uri.contains("serviceKey=Ab%2Bc%2Fd%3D%3D&"),form+" -> "+uri);
            assertFalse(uri.contains("%25"),"이중 인코딩: "+uri);
        }
    }

    @Test void serviceKeyNormalizationKeepsLiteralPlusAndHandlesEmptyAndMalformedInput(){
        assertEquals("Ab+c/d==",ServiceKeys.decoded("Ab%2Bc%2Fd%3D%3D"));
        assertEquals("Ab+c/d==",ServiceKeys.decoded("Ab+c/d=="));
        assertEquals("Ab%2Bc%2Fd%3D%3D",ServiceKeys.encoded("Ab+c/d=="));
        assertEquals("Ab%2Bc%2Fd%3D%3D",ServiceKeys.encoded("Ab%2Bc%2Fd%3D%3D"));
        assertEquals("",ServiceKeys.decoded(null));assertEquals("",ServiceKeys.encoded("  "));
        assertEquals("abc%zz",ServiceKeys.decoded("abc%zz"),"잘못된 % 시퀀스는 원문 유지");
        assertEquals("UNCONFIGURED",new NmcMedicalClient("\"\"").search(q).status());
    }

    @Test void parseInstitutionPreservesUnknownFlagsAndCoordinates(){
        var r=parse(wrap("<totalCount>21</totalCount><items><item><hpid>qa1</hpid><dutyName>검증기관</dutyName><dutyAddr>테스트 주소</dutyAddr><dutyTel1>02-123-4567</dutyTel1><wgs84Lat>37.5</wgs84Lat><wgs84Lon>127.0</wgs84Lon></item></items>"));
        assertEquals("OK",r.status());assertTrue(r.hasNext());assertEquals(21L,r.total());assertNull(r.items().get(0).emergency());assertEquals(37.5,r.items().get(0).latitude());assertEquals("HOSPITAL:qa1",r.items().get(0).id());
    }

    @Test void emptyValidDataIsDifferentFromMalformedData(){assertEquals("OK",parse(wrap("<totalCount>0</totalCount><items/>")).status());assertEquals("INVALID_RESPONSE",parse("<html>wrong</html>").status());assertEquals("INVALID_RESPONSE",parse(wrap("<totalCount>3</totalCount><items/>")).status());}

    @Test void gatewayAndAuthenticationErrorsNeverLeakRawMessage(){
        assertEquals("AUTH_ERROR",parse("<OpenAPI_ServiceResponse><cmmMsgHeader><returnAuthMsg>private-key</returnAuthMsg><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>").status());
        assertEquals("RATE_LIMITED",parse("<response><resultCode>22</resultCode></response>").status());
    }

    @Test void malformedItemsAndXmlEntitiesAreRejected(){
        assertEquals("INVALID_RESPONSE",parse(wrap("<totalCount>1</totalCount><items><item><dutyName>missing id</dutyName></item></items>")).status());
        assertEquals("INVALID_RESPONSE",parse("<!DOCTYPE x [<!ENTITY leak SYSTEM 'file:///never-read'>]><response>&leak;</response>").status());
    }

    @Test void invalidLocationAndTelephoneDoNotBecomeUsableData(){
        var r=parse(wrap("<totalCount>1</totalCount><items><item><hpid>x</hpid><dutyName>x</dutyName><dutyAddr>x</dutyAddr><dutyTel1>javascript:bad</dutyTel1><wgs84Lat>NaN</wgs84Lat><wgs84Lon>181</wgs84Lon><dutyEryn>2</dutyEryn></item></items>"));
        assertEquals("OK",r.status());var item=r.items().get(0);assertNull(item.latitude());assertNull(item.longitude());assertNull(item.tel());assertEquals(false,item.emergency());
    }
}
