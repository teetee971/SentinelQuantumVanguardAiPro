import java.security.*;
import java.util.*;
public class InspectHistoricalCertificate {
 public static void main(String[] args) throws Exception {
  KeyStore store=KeyStore.getInstance("JKS");
  try { store.load(System.in, "android".toCharArray()); }
  catch(Exception e) { System.out.println("UNVERIFIED_STORE"); System.exit(2); }
  for(String alias: Collections.list(store.aliases())) {
   java.security.cert.Certificate cert=store.getCertificate(alias);
   if(cert!=null) {
    String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
    System.out.println(digest+" private_entry="+store.entryInstanceOf(alias,KeyStore.PrivateKeyEntry.class));
   }
  }
 }
}
