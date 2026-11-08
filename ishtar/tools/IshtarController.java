import org.json.*;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Primary-user application adaptation. Never edits package identities or other users. */
public final class IshtarController {
    final ComponentController c;
    static final String COMPANION = "com.dalong.cnbridge";
    IshtarController(String path) throws Exception {
        ComponentController.STATE = new File("/data/adb/ishtar_cn");
        c = new ComponentController(path);
        if (!c.nativeAddons || c.catalog.optInt("schema") != 2) throw new IOException("按应用配置版本不匹配");
    }
    static String cmd(String... args) throws Exception { return ComponentController.cmd(args); }
    static void write(File f, JSONObject j) throws Exception { ComponentController.write(f,j); }
    static JSONObject read(File f) throws Exception { return ComponentController.read(f); }
    File state(String name) { return c.state(name); }
    static boolean on(JSONObject value, String id) throws Exception { return ComponentController.enabled(value,id); }
    Set<String> packages(JSONObject value) throws Exception {
        Set<String> result = new TreeSet<>();
        for (int i=0;i<c.features.length();i++) {
            JSONObject f=c.features.getJSONObject(i); if(!on(value,f.getString("id"))) continue;
            JSONArray list=f.getJSONArray("packages"); for(int j=0;j<list.length();j++) result.add(list.getString(j));
        }
        return result;
    }
    String revision(JSONObject value) throws Exception {
        StringBuilder canonical=new StringBuilder();
        for(int i=0;i<c.features.length();i++) {
            String id=c.features.getJSONObject(i).getString("id"); JSONObject item=value.getJSONObject(id);
            canonical.append(id).append(':').append(item.getBoolean("enabled")).append(':').append(item.getBoolean("remove")).append(';');
        }
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes("UTF-8"));
        StringBuilder out=new StringBuilder(); for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255)); return out.toString();
    }
    JSONObject companion(JSONObject value) throws Exception {
        return companion(value,value==null?"status":"configure");
    }
    JSONObject companion(JSONObject value,String operation) throws Exception {
        List<String> args=new ArrayList<>(Arrays.asList("/system/bin/content","call","--user","0","--uri","content://"+COMPANION+".config","--method",operation));
        if(value!=null) {
            JSONArray enabled=new JSONArray();
            for(int i=0;i<c.features.length();i++){String id=c.features.getJSONObject(i).getString("id");if(on(value,id))enabled.put(id);}
            JSONObject config=new JSONObject().put("enabled",enabled).put("revision",revision(value));
            args.add("--arg");args.add(Base64.getEncoder().encodeToString(config.toString().getBytes("UTF-8")));
        }
        String output=cmd(args.toArray(new String[0]));
        Matcher encoded=Pattern.compile("encoded=([A-Za-z0-9+/=]+)").matcher(output);
        if(!output.contains("ok=true")||!encoded.find())throw new IOException("配套模块未就绪：请启用 LSPosed 模块、推荐作用域并允许配套应用后台启动。"+output.substring(0,Math.min(output.length(),180)));
        return new JSONObject(new String(Base64.getDecoder().decode(encoded.group(1)),"UTF-8"));
    }
    void requireScope(JSONObject value, JSONObject remote) throws Exception {
        if(remote.getInt("api")<102)throw new IOException("需要 LSPosed API 102");
        if(remote.optInt("version")<10000)throw new IOException("请先更新国内化按应用适配 APK 至 1.0.0 或更高版本");
        Set<String> scope=new HashSet<>();JSONArray list=remote.getJSONArray("scope");
        for(int i=0;i<list.length();i++)scope.add(list.getString(i));
        Set<String> missing=packages(value); missing.removeAll(scope);
        if(!missing.isEmpty())throw new IOException("LSPosed 未勾选所需应用："+missing);
    }
    void requireAvailableScope(JSONObject value, JSONObject remote) throws Exception {
        // Uninstalled addons lose their framework scope. Request it after restoring them next boot.
        JSONObject available=new JSONObject(remote.toString());Set<String> installed=installedPrimary();
        JSONArray scope=available.getJSONArray("scope");
        for(int i=0;i<c.units.length();i++) {
            String pkg=c.units.getJSONObject(i).getString("package");
            if(!installed.contains(pkg))scope.put(pkg);
        }
        requireScope(value,available);
    }
    void requireRemote(JSONObject value) throws Exception {
        JSONObject remote=companion(null); requireScope(value,remote);
        if(!revision(value).equals(remote.optString("revision")))throw new IOException("配套模块配置与本次启动不一致");
        Set<String> actual=new TreeSet<>(),expected=new TreeSet<>();
        JSONArray ids=remote.getJSONArray("enabled");for(int i=0;i<ids.length();i++)actual.add(ids.getString(i));
        for(int i=0;i<c.features.length();i++){String id=c.features.getJSONObject(i).getString("id");if(on(value,id))expected.add(id);}
        if(!actual.equals(expected))throw new IOException("配套模块开关回读不一致");
    }
    void migrate() throws Exception {
        for(String name:Arrays.asList("applied.json","desired.json")) {
            File file=state(name);if(!file.isFile())continue;JSONObject old=read(file);
            if(!old.has("cn_region")){c.validate(old);continue;}
            if(!state("before-app-scoped/"+name).exists())write(state("before-app-scoped/"+name),old);
            JSONObject upgraded=c.defaults();boolean nativeEnabled=on(old,"cn_region");
            for(int i=0;i<c.features.length();i++) {
                JSONObject feature=c.features.getJSONObject(i);String id=feature.getString("id");
                if(old.has(id))upgraded.put(id,old.getJSONObject(id));
                else upgraded.getJSONObject(id).put("enabled",nativeEnabled);
            }
            write(file,upgraded);
        }
    }
    void properties() throws Exception {
        // Restore the exact Elite ROM baseline globally. CN exists only in selected processes.
        c.properties(new JSONObject());
    }
    void marketPermission(JSONObject wanted) throws Exception {
        File target=new File(c.mod,"system/etc/permissions/signature-permissions-ishtar-market.xml");
        if(on(wanted,"market")) {
            File source=new File(c.mod,"payload/signature-permissions-ishtar-market.xml");
            String expected=c.catalog.getJSONArray("nativeFiles").getJSONObject(0).getString("sha256");
            if(!ComponentController.hash(source).equals(expected))throw new IOException("应用商店权限源损坏");
            target.getParentFile().mkdirs();Files.copy(source.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
            ComponentController.publishPermissions(target);
        } else Files.deleteIfExists(target.toPath());
    }
    void save(JSONObject wanted) throws Exception {
        if(state("scoped-job.json").isFile()&&!c.bootId().equals(read(state("scoped-job.json")).getString("savedBoot")))
            throw new IOException("本次启动仍在应用配置，请稍后刷新");
        requireAvailableScope(wanted,companion(null));
        // Stage the next generation; running applications keep the current selection until reboot.
        JSONObject staged=companion(wanted,"stage");
        if(!revision(wanted).equals(staged.optString("stagedRevision")))throw new IOException("下次启动配置回读失败");
        write(state("scoped-job.json"),new JSONObject().put("config",wanted).put("savedBoot",c.bootId()));
        write(state("desired.json"),wanted);
        write(state("last-result.json"),new JSONObject().put("ok",true).put("bootId",c.bootId()).put("message","配置已保存，等待手动重启"));
    }
    void install() throws Exception {
        if(new File(c.mod,"next-job.json").exists()||state("mounted.json").exists())throw new IOException("请先完成旧版待应用配置再升级");
        requireAvailableScope(c.defaults(),companion(null));
        Map<String,byte[]> original=new LinkedHashMap<>();
        for(String name:Arrays.asList("applied.json","desired.json","scoped-job.json"))
            original.put(name,state(name).isFile()?Files.readAllBytes(state(name).toPath()):null);
        try {
        migrate();JSONObject wanted=c.config("desired.json");
        if(!state("applied.json").exists()) {
            c.requireNativePackagesAbsent();JSONObject off=c.defaults();
            for(int i=0;i<c.features.length();i++)off.getJSONObject(c.features.getJSONObject(i).getString("id")).put("enabled",false);
            write(state("applied.json"),off);
        }
        Set<String> libraries=new HashSet<>(Arrays.asList(cmd("/system/bin/pm","list","libraries").split("\n")));
        JSONArray required=c.catalog.getJSONArray("requiredLibraries");
        for(int i=0;i<required.length();i++)if(!libraries.contains("library:"+required.getString(i)))throw new IOException("系统缺少共享库："+required.getString(i));
        properties();marketPermission(wanted);
        companion(wanted,"stage");
        write(state("desired.json"),wanted);
        write(state("scoped-job.json"),new JSONObject().put("config",wanted).put("savedBoot",c.bootId()));
        } catch(Exception failure) {
            for(Map.Entry<String,byte[]> item:original.entrySet()) {
                if(item.getValue()==null)Files.deleteIfExists(state(item.getKey()).toPath());
                else ComponentController.writeText(state(item.getKey()),new String(item.getValue(),"UTF-8"));
            }
            throw failure;
        }
    }
    void early() throws Exception {
        if(!ComponentController.shell("pidof system_server || true").isEmpty())throw new IOException("只能在启动早期处理挂载");
        JSONObject wanted=state("scoped-job.json").isFile()?c.validate(read(state("scoped-job.json")).getJSONObject("config")):c.config("applied.json");
        properties();marketPermission(wanted);
    }
    Set<String> installedPrimary() throws Exception {
        Set<String> result=new HashSet<>();
        for(String line:cmd("/system/bin/pm","list","packages","--user","0").split("\n"))
            if(line.startsWith("package:"))result.add(line.substring(8));
        return result;
    }
    void registration(String pkg, boolean installed) throws Exception {
        String output=installed?cmd("/system/bin/cmd","package","install-existing","--user","0",pkg):
                cmd("/system/bin/pm","uninstall","-k","--user","0",pkg);
        if(!(output.contains("installed for user")||output.equals("Success")))throw new IOException("主用户组件切换失败："+pkg+" / "+output);
    }
    void verify(JSONObject wanted) throws Exception {
        c.verifyLoaded(wanted);
        File marker=new File("/system/etc/cnbridge-ishtar");
        if(!marker.isFile()||!"ishtar-elite308-scoped-v1".equals(new String(Files.readAllBytes(marker.toPath()),"UTF-8").trim()))
            throw new IOException("按应用适配未在本次启动开启");
        Set<String> installed=installedPrimary();
        for(int i=0;i<c.units.length();i++) {
            JSONObject u=c.units.getJSONObject(i);String id=u.getJSONArray("owners").getString(0);
            boolean expected=on(wanted,id)||!wanted.getJSONObject(id).getBoolean("remove");
            if(installed.contains(u.getString("package"))!=expected)throw new IOException("主用户组件安装状态不一致："+u.getString("package"));
        }
        for(String pkg:packages(wanted))if(!installed.contains(pkg))throw new IOException("所选应用未安装："+pkg);
        requireRemote(wanted);
    }
    void late() throws Exception {
        if(!"RUNNING_UNLOCKED".equals(cmd("/system/bin/cmd","activity","get-started-user-state","0")))throw new IOException("等待主用户解锁");
        JSONObject before=c.config("applied.json"), wanted=before;
        if(state("scoped-job.json").exists()) {
            JSONObject job=read(state("scoped-job.json"));
            if(c.bootId().equals(job.getString("savedBoot")))throw new IOException("配置已保存，请手动重启后应用");
            wanted=c.validate(job.getJSONObject("config"));
        }
        Set<String> installed=installedPrimary();JSONObject previous=new JSONObject();
        for(int i=0;i<c.units.length();i++){String pkg=c.units.getJSONObject(i).getString("package");previous.put(pkg,installed.contains(pkg));}
        // Persist before changing PackageManager so a retry after interruption can recover.
        File transaction=state("scoped-transaction.json");
        if(!transaction.exists())write(transaction,new JSONObject().put("before",before).put("registration",previous));
        else {JSONObject old=read(transaction);before=c.validate(old.getJSONObject("before"));previous=old.getJSONObject("registration");}
        try {
            companion(wanted);
            for(int i=0;i<c.units.length();i++) {
                JSONObject u=c.units.getJSONObject(i);String id=u.getJSONArray("owners").getString(0),pkg=u.getString("package");
                boolean next=on(wanted,id)||!wanted.getJSONObject(id).getBoolean("remove");
                if(next!=installed.contains(pkg))registration(pkg,next);
            }
            JSONObject remote=companion(null);
            try {requireScope(wanted,remote);} catch(IOException missing) {
                // Public framework API: authorization remains visible in the LSPosed notification.
                companion(wanted,"scope");
                write(state("waiting-scope.json"),new JSONObject().put("revision",revision(wanted)));
                write(state("last-result.json"),new JSONObject().put("ok",false).put("phase","scope")
                        .put("message","补装应用已恢复。请在 LSPosed 作用域请求通知中允许，然后点击“授权后重新校验”。"));
                return;
            }
            // These apps were absent at boot and may have started before framework authorization.
            Iterator<String> restored=previous.keys();
            while(restored.hasNext()){String pkg=restored.next();if(!previous.getBoolean(pkg)&&packages(wanted).contains(pkg))cmd("/system/bin/am","force-stop","--user","0",pkg);}
            // Selection was staged before reboot: no force-stop of clocks or other native apps.
            verify(wanted);write(state("applied.json"),wanted);write(state("desired.json"),wanted);
            Files.deleteIfExists(state("scoped-job.json").toPath());Files.deleteIfExists(transaction.toPath());
            Files.deleteIfExists(state("waiting-scope.json").toPath());
            c.appliedResult();
        } catch(Exception failure) {
            String rollback="";
            try {
                companion(before);Set<String> now=installedPrimary();
                Iterator<String> it=previous.keys();while(it.hasNext()){String pkg=it.next();if(previous.getBoolean(pkg)!=now.contains(pkg))registration(pkg,previous.getBoolean(pkg));}
                Files.deleteIfExists(transaction.toPath());
                Files.deleteIfExists(state("waiting-scope.json").toPath());
            } catch(Exception problem){rollback="；回退尚未完成："+problem.getMessage();}
            throw new IOException(failure.getMessage()+rollback,failure);
        }
    }
    JSONObject status() throws Exception {
        JSONObject status=c.status();boolean pending=state("scoped-job.json").exists();
        boolean applying=pending&&!c.bootId().equals(read(state("scoped-job.json")).getString("savedBoot"));
        status.put("pending",pending&&!applying).put("applying",applying);
        status.put("waitingScope",state("waiting-scope.json").isFile());
        try {requireRemote(applying?c.config("desired.json"):c.config("applied.json"));status.put("companionReady",true);}
        catch(Exception e){
            status.put("companionReady",false);
            if(!applying)status.put("result",new JSONObject().put("ok",false).put("message",e.getMessage()));
        }
        return status;
    }
    public static void main(String[] args) {
        try {
            if(args.length<2)throw new IOException("缺少操作");
            IshtarController self=new IshtarController(args[0]);self.state("").mkdirs();cmd("/system/bin/chmod","700",self.state("").getPath());
            try(RandomAccessFile file=new RandomAccessFile(self.state("config.lock"),"rw");FileLock lock=file.getChannel().lock()) {
                String op=args[1];
                if(op.equals("status")){System.out.println(self.status());return;}
                // Read-only boot preflight. Framework binding can lag behind user unlock.
                if(op.equals("ready")){self.companion(null);System.out.println("{\"ok\":true}");return;}
                if(op.equals("save")) {
                    if(args.length!=3||args[2].length()>16384||!args[2].matches("[A-Za-z0-9+/=]+"))throw new IOException("配置编码无效");
                    self.save(self.c.validate(new JSONObject(new String(Base64.getDecoder().decode(args[2]),"UTF-8"))));
                } else if(op.equals("install"))self.install();
                else if(op.equals("early"))self.early();
                else if(op.equals("late"))self.late();
                else if(op.equals("retry")) {
                    if(!self.state("waiting-scope.json").isFile())throw new IOException("当前没有等待授权的配置");
                    self.late();
                }
                else throw new IOException("不支持的操作");
                System.out.println(new JSONObject().put("ok",true).put("message","操作完成"));
            }
        } catch(Exception e) {
            try {
                JSONObject error=new JSONObject().put("ok",false).put("message",String.valueOf(e.getMessage()));
                if(args.length>1&&(args[1].equals("early")||args[1].equals("late")))write(new File("/data/adb/ishtar_cn/last-result.json"),error);
                System.out.println(error);
            }catch(Exception ignored){System.out.println("{\"ok\":false,\"message\":\"配置失败\"}");}
            System.exit(1);
        }
    }
}
