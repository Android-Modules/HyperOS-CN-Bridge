import org.json.*;
import java.io.*;
import java.util.*;

/** Read-only on-device regression checks; does not save config, alter apps, or mount files. */
public final class ScopedValidationTest {
    interface Action {void run() throws Exception;}
    static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    static void fails(Action action,String why)throws Exception{
        try{action.run();}catch(IOException|JSONException expected){return;}throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception {
        IshtarController s=new IshtarController("/data/adb/modules/ishtar_cn_addons");
        ComponentController c=s.c;
        JSONObject applied=c.config("applied.json");
        s.verify(applied);
        check(c.features.length()==19&&c.units.length()==9,"catalog dimensions");
        Set<String> seen=new HashSet<>();
        for(int i=0;i<c.features.length();i++) {
            JSONObject f=c.features.getJSONObject(i),single=c.defaults();String id=f.getString("id");
            Iterator<String> keys=single.keys();while(keys.hasNext())single.getJSONObject(keys.next()).put("enabled",false);
            check(s.packages(single).isEmpty(),"all off has no hook scope");
            single.getJSONObject(id).put("enabled",true);
            Set<String> expected=new TreeSet<>();JSONArray list=f.getJSONArray("packages");
            for(int j=0;j<list.length();j++){String pkg=list.getString(j);expected.add(pkg);check(seen.add(pkg),"shared implicit scope: "+pkg);}
            check(expected.equals(s.packages(single)),"single feature isolation: "+id);
            JSONObject scope=new JSONObject().put("api",102).put("version",10000).put("scope",new JSONArray(expected));s.requireScope(single,scope);
            scope.put("version",1);fails(()->s.requireScope(single,scope),"old companion rejected");scope.put("version",10000);
            scope.put("scope",new JSONArray());fails(()->s.requireScope(single,scope),"missing scope rejected");
        }
        for(String pkg:Arrays.asList("android","com.xiaomi.account","com.xiaomi.xmsf","com.miui.home","com.android.providers.downloads"))
            check(!seen.contains(pkg),"shared system dependency excluded: "+pkg);
        JSONObject missing=new JSONObject(applied.toString());missing.remove("calendar");fails(()->c.validate(missing),"missing switch rejected");
        JSONObject extra=new JSONObject(applied.toString());extra.put("unknown",true);fails(()->c.validate(extra),"unknown switch rejected");
        JSONObject bad=new JSONObject(applied.toString());bad.getJSONObject("calendar").put("enabled","true");fails(()->c.validate(bad),"string boolean rejected");
        JSONObject reversed=new JSONObject();List<String> keys=new ArrayList<>();applied.keys().forEachRemaining(keys::add);Collections.reverse(keys);
        for(String key:keys)reversed.put(key,applied.get(key));check(s.revision(applied).equals(s.revision(reversed)),"canonical revision independent of JSON key order");
        JSONObject compat=c.catalog.getJSONArray("vendorCompat").getJSONObject(0);String hash=compat.getString("sha256");
        compat.put("sha256",String.format("%064d",0));fails(()->c.verifyLoaded(applied),"vendor mount hash checked");compat.put("sha256",hash);
        JSONObject unit=c.units.getJSONObject(0);JSONArray owners=unit.getJSONArray("owners");String pkg=unit.getString("package");
        unit.put("owners",new JSONArray().put("calendar")).put("package","org.cnbridge.missing.probe");
        JSONObject calendar=new JSONObject(applied.toString());calendar.getJSONObject("calendar").put("enabled",true);
        fails(()->c.verifyLoaded(calendar),"missing selected package checked");unit.put("package",pkg).put("owners",owners);
        s.verify(applied);
        System.out.println("PASS: 19 isolated scopes, all-off, canonical revisions, missing/unknown/invalid switches, missing LSPosed scope, protected dependencies, missing package and QESDK hash; no device writes.");
    }
}
