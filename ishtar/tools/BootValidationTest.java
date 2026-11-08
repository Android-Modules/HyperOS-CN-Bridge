import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;

/** On-device regression probe. Uses a fresh /data/local/tmp directory for all
 * state writes; installed packages, mounts and module configuration are read-only.
 * Compile with ComponentController, dex the classes, then run via app_process.
 */
public final class BootValidationTest {
    interface Action { void run() throws Exception; }
    static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    static void fails(Action action,String message) throws Exception {
        try { action.run(); } catch(java.io.IOException expected) { return; }
        throw new AssertionError(message);
    }
    static void set(Object object,String name,Object value) throws Exception {
        Field field=ComponentController.class.getDeclaredField(name);
        field.setAccessible(true);field.set(object,value);
    }
    public static void main(String[] args) throws Exception {
        check(args.length==1,"Supply a fresh /data/local/tmp/ishtar-boot-test-* directory");
        File scratch=new File(args[0]).getCanonicalFile();
        check(scratch.getParent().equals("/data/local/tmp")&&scratch.getName().startsWith("ishtar-boot-test-"),"Unsafe scratch path");
        check(!scratch.exists()&&scratch.mkdir(),"Scratch must not exist");
        ComponentController c=new ComponentController("/data/adb/modules/ishtar_cn_addons");
        JSONObject wanted=c.config("applied.json");
        File mod=new File(scratch,"module");check(mod.mkdir(),"Create fixture module");
        set(c,"mod",mod);ComponentController.STATE=new File(scratch,"state");
        ComponentController.write(c.state("desired.json"),wanted);
        ComponentController.write(c.state("applied.json"),wanted);
        String before=ComponentController.hash(c.state("applied.json"));
        ComponentController.write(c.state("last-result.json"),new JSONObject().put("ok",true).put("phase","applied").put("bootId","previous-boot"));
        check(!c.status().getBoolean("bootVerified"),"Old success must be stale");
        c.late();
        check(c.status().getBoolean("bootVerified"),"Ordinary boot must be verified");
        String success=ComponentController.hash(c.state("last-result.json"));

        JSONObject unit=c.units.getJSONObject(0);String pkg=unit.getString("package");
        unit.put("package","org.cnbridge.missing.regression.probe");
        fails(c::late,"Missing package must fail");unit.put("package",pkg);
        JSONObject apk=null;
        for(int i=0;i<unit.getJSONArray("files").length();i++) {
            JSONObject file=unit.getJSONArray("files").getJSONObject(i);
            if(file.getString("path").endsWith(".apk")){apk=file;break;}
        }
        check(apk!=null,"Fixture needs base APK");String digest=apk.getString("sha256");
        apk.put("sha256",String.format("%064d",0));fails(c::late,"Wrong mounted APK hash must fail");apk.put("sha256",digest);
        JSONObject grant=c.catalog.getJSONArray("nativeFiles").getJSONObject(0);String path=grant.getString("path");
        grant.put("path","system/etc/permissions/cnbridge-missing-test.xml");
        fails(c::late,"Missing permission mount must fail");grant.put("path",path);
        File pending=new File(mod,"next-job.json");ComponentController.write(pending,new JSONObject());
        fails(c::late,"Pending transaction must not be declared applied");Files.delete(pending.toPath());
        JSONObject wrong=new JSONObject(wanted.toString());wrong.getJSONObject("cn_region").put("enabled",!wanted.getJSONObject("cn_region").getBoolean("enabled"));
        ComponentController.write(c.state("applied.json"),wrong);fails(c::late,"Wrong region must fail");
        Files.delete(c.state("applied.json").toPath());fails(c::late,"Missing applied config must fail");
        ComponentController.write(c.state("applied.json"),wanted);
        check(success.equals(ComponentController.hash(c.state("last-result.json"))),"Failed validation must not write success");
        set(c,"nativeAddons",false);c.late();
        check(success.equals(ComponentController.hash(c.state("last-result.json"))),"Songyuan no-job behavior must stay unchanged");
        check(before.equals(ComponentController.hash(c.state("applied.json"))),"Applied fixture changed");
        check(before.equals(ComponentController.hash(c.state("desired.json"))),"Desired fixture changed");
        System.out.println("PASS: fresh boot, stale success, missing package, mount hash, missing grant, pending job, wrong region, missing config, songyuan isolation; all writes confined to scratch.");
    }
}
