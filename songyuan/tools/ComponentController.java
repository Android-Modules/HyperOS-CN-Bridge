import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Root-only, offline controller. No user supplied shell, paths or package IDs. */
public final class ComponentController {
    static File STATE = new File("/data/adb/songyuan_cn");
    static final String TSM = "com.miui.tsmclient", POCO = "com.mi.android.globallauncher", HOME = "com.miui.home";
    final File mod;
    final JSONObject catalog;
    final JSONArray units, features;
    final boolean nativeAddons;
    ComponentController(String path) throws Exception {
        mod = new File(path).getCanonicalFile();
        if (!(mod.getName().equals("songyuan_cn_complete") || mod.getName().equals("ishtar_cn_addons")) ||
                !(mod.getParent().equals("/data/adb/modules") || mod.getParent().equals("/data/adb/modules_update")))
            throw new IOException("模块路径无效");
        catalog = read(new File(mod, "catalog.json"));
        nativeAddons = mod.getName().equals("ishtar_cn_addons");
        if(nativeAddons) {
            if(!"ishtar-elite308-addons".equals(catalog.optString("profile")) ||
                    !"ishtar".equals(cmd("/system/bin/getprop","ro.product.device")) ||
                    !"OS3.0.308.0.WMACNXM".equals(cmd("/system/bin/getprop","ro.build.version.incremental")) ||
                    !"EliteRomLite".equals(cmd("/system/bin/getprop","ro.build.elite.romvariant")) ||
                    !"v4.05".equals(cmd("/system/bin/getprop","ro.build.elite.versioncode")))
                throw new IOException("ishtar 补全配置与当前系统不匹配");
        } else if(catalog.has("profile")) throw new IOException("模块配置类型不匹配");
        units = catalog.getJSONArray("units"); features = catalog.getJSONArray("features");
    }
    static String quote(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
    static String cmd(String... args) throws Exception {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) { pipe(in, out); }
        int result = p.waitFor();
        String s = new String(out.toByteArray(), "UTF-8").trim();
        if (result != 0) throw new IOException("命令失败：" + args[0] + " (" + result + ") " + s.substring(0, Math.min(220, s.length())));
        return s;
    }
    static String shell(String s) throws Exception { return cmd("/system/bin/sh", "-c", s); }
    static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[262144]; int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
    }
    static JSONObject read(File f) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(f.toPath()), "UTF-8"));
    }
    static void write(File f, JSONObject j) throws Exception { writeText(f, j.toString(2) + "\n"); }
    static void writeText(File f, String s) throws Exception {
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".new");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(s.getBytes("UTF-8")); out.getFD().sync(); }
        tmp.setReadable(false, false); tmp.setWritable(false, false);
        tmp.setReadable(true, true); tmp.setWritable(true, true);
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    static void remove(File f) throws IOException {
        if (Files.isSymbolicLink(f.toPath())) throw new IOException("拒绝跟随符号链接：" + f.getName());
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children == null) throw new IOException("无法读取目录：" + f.getName());
            for (File c : children) remove(c);
        }
        if (f.exists() && !f.delete()) throw new IOException("无法移除文件：" + f.getName());
    }
    static String hash(File f) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) { byte[] b = new byte[262144]; int n; while ((n = in.read(b)) != -1) d.update(b, 0, n); }
        StringBuilder s = new StringBuilder(); for (byte b : d.digest()) s.append(String.format(Locale.ROOT, "%02x", b & 255)); return s.toString();
    }
    static void publishPermissions(File file) throws Exception {
        if(Files.isSymbolicLink(file.toPath()))throw new IOException("组件树不允许符号链接");
        Files.setPosixFilePermissions(file.toPath(),PosixFilePermissions.fromString(file.isDirectory()?"rwxr-xr-x":"rw-r--r--"));
        if(file.isDirectory()) {
            File[] children=file.listFiles();if(children==null)throw new IOException("组件目录不可读");
            for(File child:children)publishPermissions(child);
        }
    }
    File state(String name) { return new File(STATE, name); }
    static File safe(File base, String path) throws IOException {
        if (!path.matches("(?:system|payload)/[a-zA-Z0-9_./+-]+") || path.contains("..")) throw new IOException("组件路径无效："+path);
        File f = new File(base, path);
        if (!f.getCanonicalPath().startsWith(base.getCanonicalPath() + "/")) throw new IOException("组件路径越界");
        return f;
    }
    JSONObject defaults() throws Exception {
        JSONObject result = new JSONObject();
        for (int i=0;i<features.length();i++) result.put(features.getJSONObject(i).getString("id"), new JSONObject().put("enabled", true).put("remove", true));
        return result;
    }
    JSONObject config(String name) throws Exception {
        return state(name).isFile() ? validate(read(state(name))) : defaults();
    }
    JSONObject validate(JSONObject value) throws Exception {
        if (value.length() != features.length()) throw new IOException("组件配置不完整");
        for (int i=0;i<features.length();i++) {
            String id=features.getJSONObject(i).getString("id"); JSONObject item=value.getJSONObject(id);
            if (item.length()!=2 || !(item.get("enabled") instanceof Boolean) || !(item.get("remove") instanceof Boolean)) throw new IOException("组件配置无效："+id);
        }
        return value;
    }
    static boolean any(JSONObject c) throws Exception { Iterator<String> it=c.keys(); while(it.hasNext()) if(c.getJSONObject(it.next()).getBoolean("enabled")) return true; return false; }
    static boolean enabled(JSONObject c, String id) throws Exception { return c.has(id) && c.getJSONObject(id).getBoolean("enabled"); }
    boolean active(JSONObject u, JSONObject c) throws Exception {
        JSONArray owners=u.getJSONArray("owners");
        for(int i=0;i<owners.length();i++) if(owners.getString(i).equals("*") ? any(c) : enabled(c,owners.getString(i))) return true;
        return false;
    }
    boolean keep(JSONObject u, JSONObject c) throws Exception {
        JSONArray owners=u.getJSONArray("owners");
        for(int i=0;i<owners.length();i++) {
            String id=owners.getString(i);
            if(id.equals("*")) { Iterator<String> it=c.keys(); while(it.hasNext()) if(!c.getJSONObject(it.next()).getBoolean("remove")) return true; }
            else if(c.has(id) && !c.getJSONObject(id).getBoolean("remove")) return true;
        }
        return false;
    }
    void snapshot(String pkg) throws Exception {
        // Snapshot only credential-unlocked user 0; never guess about encrypted data.
        File dest=state("app-data/"+pkg); dest.mkdirs();
        String ce="/data/user/0/"+pkg, de="/data/user_de/0/"+pkg;
        if(!new File(ce).isDirectory() && !new File(de).isDirectory()) return;
        shell("am force-stop "+pkg+" >/dev/null 2>&1 || true");
        try {
            List<String> args=new ArrayList<>(Arrays.asList("/system/bin/tar","-cpf",new File(dest,"data.tar.new").getPath(),"-C","/"));
            if(new File(ce).isDirectory()) args.add(ce.substring(1));
            if(new File(de).isDirectory()) args.add(de.substring(1));
            cmd(args.toArray(new String[0]));
            Files.move(new File(dest,"data.tar.new").toPath(),new File(dest,"data.tar").toPath(),StandardCopyOption.REPLACE_EXISTING);
            shell("chmod 700 "+quote(dest.getPath())+"; chmod 600 "+quote(new File(dest,"data.tar").getPath()));
        } finally { shell("cmd package unstop --user 0 "+pkg+" >/dev/null 2>&1 || true"); }
    }
    void restore(String pkg) throws Exception {
        File archive=state("app-data/"+pkg+"/data.tar"); if(!archive.isFile()) return;
        // Ensure PackageManager has created the right UID before replacing files.
        File de=new File("/data/user_de/0/"+pkg), ce=new File("/data/user/0/"+pkg);
        if(!de.isDirectory() && !ce.isDirectory()) return;
        String owner=cmd("/system/bin/stat","-c","%u:%g",de.isDirectory()?de.getPath():ce.getPath());
        File temp=state("restore-temp"); if(temp.exists()) remove(temp); temp.mkdirs();
        cmd("/system/bin/tar","-xpf",archive.getPath(),"-C",temp.getPath());
        shell("am force-stop "+pkg+" >/dev/null 2>&1 || true");
        for(String base:Arrays.asList("/data/user/0/","/data/user_de/0/")) {
            File from=new File(temp,base.substring(1)+pkg), to=new File(base+pkg);
            if(!from.isDirectory() || !to.isDirectory()) continue;
            cmd("/system/bin/cp","-a",from.getPath()+"/.",to.getPath()+"/");
            cmd("/system/bin/chown","-R",owner,to.getPath()); cmd("/system/bin/restorecon","-RF",to.getPath());
        }
        remove(temp); shell("cmd package unstop --user 0 "+pkg+" >/dev/null 2>&1 || true");
    }
    void prepareDownloads(JSONObject job) throws Exception {
        File db=new File("/data/user/0/com.android.providers.downloads/databases/downloads.db");
        if(!db.isFile())return;
        String id=Long.toString(job.getLong("id"));File folder=state("download-next/"+id);folder.mkdirs();
        shell("am force-stop com.android.providers.downloads >/dev/null 2>&1 || true");
        try {
            for(File file:db.getParentFile().listFiles())if(file.getName().startsWith("downloads.db"))
                Files.copy(file.toPath(),new File(folder,file.getName()).toPath(),StandardCopyOption.REPLACE_EXISTING);
            File copy=new File(folder,"downloads.db");DeviceRepair.downloads(copy,any(job.getJSONObject("config")));
            job.put("downloadSnapshot",hash(copy));
            shell("chmod 700 "+quote(folder.getPath())+"; chmod 600 "+quote(folder.getPath())+"/*");
        } finally {shell("cmd package unstop --user 0 com.android.providers.downloads >/dev/null 2>&1 || true");}
    }
    void startDownloadWorker(JSONObject job) throws Exception {
        if(!job.has("downloadSnapshot"))return;
        String id=Long.toString(job.getLong("id"));
        File copy=state("download-next/"+id+"/downloads.db");
        if(!copy.isFile()||!hash(copy).equals(job.getString("downloadSnapshot")))throw new IOException("下载数据库迁移源校验失败");
        shell("CLASSPATH="+quote(new File(mod,"repair.jar").getPath())+" /system/bin/app_process /system/bin ComponentController "+quote(mod.getPath())+" unlock-downloads "+id+" </dev/null >"+quote(state("downloads-unlock-"+id+".txt").getPath())+" 2>&1 &");
    }
    void unlockDownloads(String id) throws Exception {
        if(!id.matches("[0-9]{10,16}"))throw new IOException("下载迁移编号无效");
        File mounted=state("mounted.json"),folder=state("download-next/"+id),copy=new File(folder,"downloads.db");
        JSONObject job=null;
        for(int i=0;i<3000;i++) {
            if(mounted.isFile()) {JSONObject candidate=read(mounted);if(Long.toString(candidate.getLong("id")).equals(id)){job=candidate;break;}}
            Thread.sleep(20);
        }
        if(job==null)throw new IOException("下载迁移对应的启动未提交");
        if(!hash(copy).equals(job.getString("downloadSnapshot")))throw new IOException("下载迁移源已变化");
        File data=new File("/data/user/0/com.android.providers.downloads"),dbDir=new File(data,"databases");
        // CE keys are installed during user unlock. Preload this process before
        // framework startup, then prepare the DB as soon as CE accepts writes.
        for(int i=0;i<36000;i++) {
            try {
                if(!data.isDirectory())throw new IOException("等待下载器数据目录");
                File probe=File.createTempFile(".songyuan-unlock-",".tmp",data);
                Files.delete(probe.toPath());break;
            } catch(IOException e){if(i==35999)throw e;Thread.sleep(100);}
        }
        String owner=cmd("/system/bin/stat","-c","%u:%g",data.getPath());
        shell("am force-stop com.android.providers.downloads >/dev/null 2>&1 || true");
        try {
            dbDir.mkdirs();File target=new File(dbDir,"downloads.db"),next=new File(dbDir,".songyuan-downloads.new");
            Files.copy(copy.toPath(),next.toPath(),StandardCopyOption.REPLACE_EXISTING);
            cmd("/system/bin/chown",owner,next.getPath());cmd("/system/bin/chmod","660",next.getPath());
            cmd("/system/bin/restorecon","-F",next.getPath());
            File prior=new File(folder,"replaced");prior.mkdirs();
            for(File file:dbDir.listFiles())if(file.getName().startsWith("downloads.db")) {
                File backup=new File(prior,file.getName());
                if(file.equals(target))Files.copy(file.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING);
                else Files.move(file.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(next.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            cmd("/system/bin/restorecon","-F",target.getPath());
            write(state("downloads-ready-"+id+".json"),new JSONObject().put("ok",true).put("id",id).put("version",any(job.getJSONObject("config"))?125:123));
        } finally {shell("cmd package unstop --user 0 com.android.providers.downloads >/dev/null 2>&1 || true");}
    }
    void prepare(JSONObject wanted, boolean installing, boolean upgrading) throws Exception {
        if(state("mounted.json").isFile())throw new IOException("上次启动的配置尚未完成，请解锁手机后刷新状态");
        JSONObject before=config("applied.json");
        boolean unchanged=true;
        for(int i=0;i<features.length();i++) {
            String id=features.getJSONObject(i).getString("id");
            if(enabled(before,id)!=enabled(wanted,id) || before.getJSONObject(id).getBoolean("remove")!=wanted.getJSONObject(id).getBoolean("remove")) unchanged=false;
        }
        File[] users=new File("/data/user_de").listFiles();
        if(users==null || !new File("/data/user_de/0").isDirectory())throw new IOException("无法确认主用户数据目录");
        // A same-configuration module upgrade does not switch package identities
        // or user data. XSpace must not prevent installing maintenance fixes.
        // Keep the single-user restriction for component/data transitions.
        // A native-ROM first install only adds absent packages, without moving
        // existing identities or data. Verify absence before allowing profiles.
        boolean additionsOnly=nativeAddons&&!upgrading;
        if(additionsOnly) for(int i=0;i<units.length();i++) {
            JSONObject u=units.getJSONObject(i);String pkg=u.getString("package");
            if(active(u,before)||u.getBoolean("globalPresent")||
                    !shell("pm path --user 0 "+pkg+" 2>/dev/null || true").isEmpty()) additionsOnly=false;
            for(File user:users) if(new File(user,pkg).exists()) additionsOnly=false;
        }
        if(users.length!=1 && (!upgrading || !unchanged) && !additionsOnly)throw new IOException("检测到应用双开或其他用户：仅允许缺失组件的首次补装或保持原配置升级；组件数据切换仅支持主用户 0");
        if(!"RUNNING_UNLOCKED".equals(cmd("/system/bin/cmd","activity","get-started-user-state","0"))) throw new IOException("请解锁手机后保存，应用数据需要先备份");
        if(!state("source.zip").isFile()) throw new IOException("缺少本地安装源，请重新安装完整模块后再保存");
        JSONArray backups=new JSONArray(), restores=new JSONArray();
        File next=new File(mod,"prepare-system"); if(next.exists()) remove(next); next.mkdirs();
        try (ZipFile zip=new ZipFile(state("source.zip"))) {
            ZipEntry c=zip.getEntry("catalog.json"); if(c==null) throw new IOException("安装源不包含组件清单");
            ByteArrayOutputStream b=new ByteArrayOutputStream(); try(InputStream in=zip.getInputStream(c)){pipe(in,b);}
            if(!new JSONObject(new String(b.toByteArray(),"UTF-8")).toString().equals(catalog.toString())) throw new IOException("安装源与模块版本不一致，请重新安装");
            for(int i=0;i<units.length();i++) {
                JSONObject u=units.getJSONObject(i); String pkg=u.getString("package");
                boolean on=active(u,wanted), was=active(u,before);
                if(was && !on) { snapshot(pkg); backups.put(pkg); }
                if(!was && on) {if(u.getBoolean("globalPresent"))snapshot(pkg);restores.put(pkg);}
                if(!on) continue;
                JSONArray files=u.getJSONArray("files");
                for(int k=0;k<files.length();k++) {
                    JSONObject file=files.getJSONObject(k); String path=file.getString("path");
                    if(!path.startsWith("system/")) continue;
                    File target=safe(mod,"system/"+path.substring(7));
                    target=new File(next,path.substring(7)); target.getParentFile().mkdirs();
                    File source=safe(mod,path);
                    if(!source.isFile()) source=safe(state("retained"),path);
                    if(source.isFile() && hash(source).equals(file.getString("sha256"))) {
                        try { Files.createLink(target.toPath(),source.toPath()); }
                        catch(IOException|UnsupportedOperationException e) { Files.copy(source.toPath(),target.toPath()); }
                    } else {
                        ZipEntry entry=zip.getEntry(path);
                        if(entry==null && path.endsWith("/.replace") && file.getString("sha256").equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")) {
                            if(!target.createNewFile())throw new IOException("无法生成目录替换标记");
                        } else {
                            if(entry==null || entry.isDirectory())throw new IOException("安装源缺少文件："+path);
                            try(InputStream in=zip.getInputStream(entry);OutputStream out=new FileOutputStream(target)){pipe(in,out);}
                        }
                        if(!hash(target).equals(file.getString("sha256"))) throw new IOException("组件完整性校验失败："+pkg);
                    }
                    target.setReadable(true,false);
                }
            }
            if(nativeAddons && catalog.has("nativeFiles")) {
                JSONArray files=catalog.getJSONArray("nativeFiles");
                for(int i=0;i<files.length();i++) {
                    JSONObject file=files.getJSONObject(i);if(!enabled(wanted,file.getString("feature")))continue;
                    String path=file.getString("path");safe(mod,path);
                    if(!path.startsWith("system/etc/permissions/"))throw new IOException("原系统权限配置路径无效");
                    File target=new File(next,path.substring(7));target.getParentFile().mkdirs();
                    ZipEntry entry=zip.getEntry(path);if(entry==null||entry.isDirectory())throw new IOException("安装源缺少原系统权限配置");
                    try(InputStream in=zip.getInputStream(entry);OutputStream out=new FileOutputStream(target)){pipe(in,out);}
                    if(!hash(target).equals(file.getString("sha256")))throw new IOException("原系统权限配置完整性校验失败");
                    target.setReadable(true,false);
                }
            }
            if(!nativeAddons && !enabled(wanted,"clock")) {
                JSONArray fallback=catalog.getJSONArray("clockFallback");
                for(int i=0;i<fallback.length();i++) {
                    JSONObject item=fallback.getJSONObject(i);String path=item.getString("path");
                    safe(mod,path); // validate before stripping the known prefix
                    if(!path.startsWith("payload/GlobalDeskClock/"))throw new IOException("国际版时钟路径无效");
                    File target=new File(next,"product/app/MIUIDeskClockGlobal/"+path.substring("payload/GlobalDeskClock/".length()));
                    target.getParentFile().mkdirs();ZipEntry entry=zip.getEntry(path);if(entry==null)throw new IOException("安装源缺少国际版时钟");
                    try(InputStream in=zip.getInputStream(entry);OutputStream out=new FileOutputStream(target)){pipe(in,out);}
                    if(!hash(target).equals(item.getString("sha256")))throw new IOException("国际版时钟完整性校验失败");target.setReadable(true,false);
                }
                File flag=new File(next,"product/app/MIUIDeskClockGlobal/.replace");flag.createNewFile();flag.setReadable(true,false);
                if(!cmd("/system/bin/pm","list","packages","com.android.deskclock").contains("package:com.android.deskclock"))restores.put("com.android.deskclock");
            }
            // app_process inherits KernelSU's private umask. PackageParser must
            // traverse every directory as system_server, not just read the APK.
            publishPermissions(next);
        } catch(Exception e) { remove(next); throw e; }
        // Launcher namespaces differ; save the outgoing layout before boot scanning.
        if(!nativeAddons && enabled(before,"cloud")!=enabled(wanted,"cloud")) snapshot(enabled(before,"cloud")?HOME:POCO);
        if(!nativeAddons && installing && new File("/data/user_de/0/"+TSM).isDirectory()) snapshot(TSM);
        if(!nativeAddons && !any(before)&&any(wanted)) {
            // Install the paired auth update while GB is still active. Otherwise
            // CN region can start the Global clipboard service before late().
            File auth=new File(mod,"payload/AuthManager/AuthManager.apk");
            String result=shell("pm install -r --user 0 -S "+auth.length()+" < "+quote(auth.getPath()));
            if(!result.contains("Success"))throw new IOException("国行权限管理器准备失败");
        }
        JSONObject job=new JSONObject().put("config",wanted).put("before",before).put("backups",backups)
                .put("restores",restores).put("downloadDbPresent",new File("/data/user/0/com.android.providers.downloads/databases/downloads.db").isFile())
                .put("downloadMigrationRequired",!nativeAddons&&installing)
                .put("id",System.currentTimeMillis());
        if(!nativeAddons && (installing || any(wanted)!=any(before)))prepareDownloads(job);
        File prior=new File(mod,"prior-next-system"), ready=new File(mod,"next-system");
        if(prior.exists())remove(prior);
        if(ready.exists())Files.move(ready.toPath(),prior.toPath());
        try {
            Files.move(next.toPath(),ready.toPath());
            write(new File(mod,"next-job.json"),job); // commit marker, last
            write(state("desired.json"),wanted);
        } catch(Exception e) {
            if(ready.exists())remove(ready);
            if(prior.exists())Files.move(prior.toPath(),ready.toPath());
            throw e;
        }
        if(prior.exists())remove(prior);
        write(state("last-result.json"),new JSONObject().put("ok",true).put("message","配置已保存，等待手动重启").put("phase","pending"));
    }
    static Element top(Element root,String tag,String name) {
        for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element) {
            Element e=(Element)n; if(e.getTagName().equals(tag)&&e.getAttribute("name").equals(name)) return e;
        } return null;
    }
    void requireNativePackagesAbsent() throws Exception {
        Element settings=xml(cmd("/system/bin/abx2xml","/data/system/packages.xml","-"));
        File[] users=new File("/data/user_de").listFiles();
        if(users==null)throw new IOException("无法确认用户数据目录");
        for(int i=0;i<units.length();i++) {
            JSONObject unit=units.getJSONObject(i);String pkg=unit.getString("package");
            if(unit.getBoolean("globalPresent") || top(settings,"package",pkg)!=null)
                throw new IOException("首次补装拒绝覆盖已登记的组件："+pkg);
            for(File user:users)if(new File(user,pkg).exists() || new File("/data/user/"+user.getName(),pkg).exists())
                throw new IOException("首次补装发现已有用户数据，请先核对组件身份："+pkg);
        }
    }
    static Element xml(String s) throws Exception {
        // Android's DOM provider does not implement the desktop SAX feature URIs.
        // Reject declarations explicitly; packages.xml never needs a DTD/entity.
        if(s.contains("<!DOCTYPE")||s.contains("<!ENTITY"))throw new IOException("包设置包含不允许的 XML 声明");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(s.getBytes("UTF-8"))).getDocumentElement();
    }
    void tsmIdentity(boolean cn, String settings) throws Exception {
        Element root=xml(settings), tsm=top(root,"package",TSM), nfc=top(root,"shared-user","android.uid.nfc");
        if(tsm==null) return; // first install without a previous client
        boolean shared=tsm.getAttribute("sharedUserId").equals("1027"); if(shared==cn) return;
        if(nfc==null || !nfc.getAttribute("userId").equals("1027")) throw new IOException("NFC 共享身份校验失败");
        Set<String> a=new HashSet<>(),b=new HashSet<>();
        for(Element e:Arrays.asList(tsm,nfc)) {
            org.w3c.dom.NodeList sig=e.getElementsByTagName("cert"); Set<String> set=e==tsm?a:b;
            for(int i=0;i<sig.getLength();i++) set.add(((Element)sig.item(i)).getAttribute("index"));
        }
        if(a.isEmpty()||!a.equals(b)) throw new IOException("智能卡与 NFC 签名身份不一致");
        File[] users=new File("/data/user").listFiles(); if(users==null||users.length!=1||!users[0].getName().equals("0")) throw new IOException("智能卡身份切换目前仅支持主用户");
        JSONObject identity=state("tsm-identity.json").isFile()?read(state("tsm-identity.json")):new JSONObject();
        int uid;
        if(cn) {
            uid=Integer.parseInt(tsm.getAttribute("userId")); if(uid<10000||uid>=20000) throw new IOException("智能卡原 UID 异常");
            identity.put("globalUid",uid); write(state("tsm-identity.json"),identity); uid=1027;
        } else {
            uid=identity.optInt("globalUid",10261); Set<Integer> used=new HashSet<>();
            for(Node node=root.getFirstChild();node!=null;node=node.getNextSibling()) if(node instanceof Element && node!=tsm) {
                String s=((Element)node).getAttribute("userId"); if(s.matches("[0-9]+")) used.add(Integer.parseInt(s));
            }
            while(used.contains(uid)&&uid<20000) uid++; if(uid>=20000) throw new IOException("无法分配国际版智能卡 UID");
            identity.put("globalUid",uid); write(state("tsm-identity.json"),identity);
        }
        Pattern p=Pattern.compile("<(package|updated-package)\\s+[^>]*\\bname=\"com\\.miui\\.tsmclient\"[^>]*>"); Matcher m=p.matcher(settings);
        StringBuffer buffer=new StringBuffer();int primary=0,secondary=0;
        while(m.find()) {
            String tag=m.group(),changed=cn?tag.replaceFirst("\\buserId=\"[0-9]+\"","sharedUserId=\"1027\""):tag.replace("sharedUserId=\"1027\"","userId=\""+uid+"\"");
            if(m.group(1).equals("package")){primary++;if(changed.equals(tag))throw new IOException("智能卡设置项无法转换");}else secondary++;
            m.appendReplacement(buffer,Matcher.quoteReplacement(changed));
        }
        m.appendTail(buffer);String updated=buffer.toString();
        if(primary!=1||secondary>1)throw new IOException("智能卡设置项不唯一");
        File backup=state("tsm-last-transition"); if(backup.exists()) remove(backup); backup.mkdirs();
        writeText(new File(backup,"packages-updated.xml"),updated);
        cmd("/system/bin/xml2abx",new File(backup,"packages-updated.xml").getPath(),new File(backup,"packages-updated.abx").getPath());
        Element check=top(xml(cmd("/system/bin/abx2xml",new File(backup,"packages-updated.abx").getPath(),"-")),"package",TSM);
        if(!check.getAttribute(cn?"sharedUserId":"userId").equals(String.valueOf(uid))) throw new IOException("智能卡 ABX 校验失败");
        List<String> replaced=new ArrayList<>();
        try {
            for(String suffix:Arrays.asList("",".reservecopy",".fallback")) {
                File f=new File("/data/system/packages.xml"+suffix); if(!f.isFile()) continue;
                Files.copy(f.toPath(),new File(backup,"packages.xml"+suffix).toPath());
                File temp=new File(f.getPath()+".songyuan-new"); Files.copy(new File(backup,"packages-updated.abx").toPath(),temp.toPath(),StandardCopyOption.REPLACE_EXISTING);
                cmd("/system/bin/chown","1000:1000",temp.getPath()); cmd("/system/bin/chmod","660",temp.getPath());
                cmd("/system/bin/chcon","u:object_r:system_data_file:s0",temp.getPath());
                Files.move(temp.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); replaced.add(suffix);
            }
            for(String base:Arrays.asList("/data/user/0/","/data/user_de/0/")) {
                File data=new File(base+TSM); if(!data.isDirectory()) continue;
                // DE must be available. CE may still be locked; installd and late()
                // finish its ownership after unlock, with an unlocked snapshot saved.
                String command="chown -R "+uid+":"+uid+" "+quote(data.getPath());
                if(cn) command+=" && chcon -R u:object_r:nfc_data_file:s0 "+quote(data.getPath());
                try{shell(command);}catch(Exception e){if(base.contains("user_de"))throw e;}
            }
            writeText(state("tsm-finish"),String.valueOf(uid));
        } catch(Exception e) {
            for(String suffix:replaced) { File target=new File("/data/system/packages.xml"+suffix), temp=new File(target.getPath()+".songyuan-new");
                Files.copy(new File(backup,"packages.xml"+suffix).toPath(),temp.toPath(),StandardCopyOption.REPLACE_EXISTING);
                cmd("/system/bin/chown","1000:1000",temp.getPath());cmd("/system/bin/chmod","660",temp.getPath());cmd("/system/bin/chcon","u:object_r:system_data_file:s0",temp.getPath());
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING); }
            throw e;
        }
    }
    void rollbackTsm(Element original) throws Exception {
        File backup=state("tsm-last-transition");
        for(String suffix:Arrays.asList("",".reservecopy",".fallback")) {
            File from=new File(backup,"packages.xml"+suffix);if(!from.isFile())continue;
            File to=new File("/data/system/packages.xml"+suffix),temp=new File(to.getPath()+".songyuan-new");
            Files.copy(from.toPath(),temp.toPath(),StandardCopyOption.REPLACE_EXISTING);
            cmd("/system/bin/chown","1000:1000",temp.getPath());cmd("/system/bin/chmod","660",temp.getPath());cmd("/system/bin/chcon","u:object_r:system_data_file:s0",temp.getPath());
            Files.move(temp.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
        String uid=original.hasAttribute("sharedUserId")?original.getAttribute("sharedUserId"):original.getAttribute("userId");
        for(String base:Arrays.asList("/data/user/0/","/data/user_de/0/")) if(new File(base+TSM).isDirectory()) {
            shell("chown -R "+uid+":"+uid+" "+quote(base+TSM)+"; "+(uid.equals("1027")?"chcon -R u:object_r:nfc_data_file:s0 ":"restorecon -RF ")+quote(base+TSM)+" 2>/dev/null || true");
        }
        Files.deleteIfExists(state("tsm-finish").toPath());
    }
    void properties(JSONObject c) throws Exception {
        if(nativeAddons) {
            boolean cn=enabled(c,"cn_region");
            String props="ro.product.mod_device="+(cn?"ishtar":"ishtar_elite_global")+"\nro.miui.region="+(cn?"CN":"HK")+"\nro.miui.cust_variant="+(cn?"cn":"hk")+
                    "\nro.vendor.miui.region="+(cn?"CN":"HK")+"\nro.vendor.miui.cust_variant="+(cn?"cn":"hk")+"\n";
            // This ROM uses the integrated ThemeManager. Enabling the separate
            // ThemeStore mode hides its launcher and redirects to an absent APK.
            writeText(new File(mod,"system.prop"),props);new File(mod,"system.prop").setReadable(true,false);
            writeText(new File(mod,"cust_variant"),cn?"cn\n":"hk\n");new File(mod,"cust_variant").setReadable(true,false);
            return;
        }
        boolean on=any(c), cloud=enabled(c,"cloud"), theme=enabled(c,"theme");
        String props="ro.product.mod_device="+(on?"songyuan":"songyuan_global")+"\nro.miui.region="+(on?"CN":"GB")+
                "\nro.miui.product.home="+(cloud?HOME:POCO)+"\nro.miui.support.system.app.uninstall.v2="+theme+"\nro.vendor.audio.aiasst.support="+enabled(c,"aiasst")+"\n";
        if(enabled(c,"tsm")||enabled(c,"wallet")) props+="ro.vendor.se.type=eSE,HCE,UICC\n";
        writeText(new File(mod,"system.prop"),props); new File(mod,"system.prop").setReadable(true,false);
        writeText(new File(mod,"cust_variant"),on?"cn\n":"gb\n"); new File(mod,"cust_variant").setReadable(true,false);
    }
    void early() throws Exception {
        if(!shell("pidof system_server || true").isEmpty())throw new IOException("组件切换只能在系统扫描应用之前执行，请手动重启");
        File marker=new File(mod,"next-job.json"); if(!marker.isFile()) {properties(state("mounted.json").isFile()?read(state("mounted.json")).getJSONObject("config"):config("applied.json"));return;}
        JSONObject job=read(marker), wanted=validate(job.getJSONObject("config"));
        File next=new File(mod,"next-system"), current=new File(mod,"system"), old=new File(mod,"previous-system");
        if(!next.isDirectory()) throw new IOException("待应用的组件文件不完整，请重新保存");
        publishPermissions(next);
        if(old.exists()) throw new IOException("上次启动尚未完成，请等待系统启动后重试");
        // Framework is not running yet. Only catalog package updates are detached.
        String settings=cmd("/system/bin/abx2xml","/data/system/packages.xml","-"); Element root=xml(settings);
        JSONArray moved=new JSONArray();
        Element originalTsm=top(root,"package",TSM);
        boolean identityChange=!nativeAddons && originalTsm!=null && originalTsm.getAttribute("sharedUserId").equals("1027")!=enabled(wanted,"tsm");
        if(identityChange && state("tsm-last-transition").exists())remove(state("tsm-last-transition"));
        try {
        startDownloadWorker(job);
        for(int i=0;i<units.length();i++) {
            JSONObject u=units.getJSONObject(i); if(active(u,wanted)==active(u,job.getJSONObject("before"))) continue;
            String pkg=u.getString("package"); Element e=top(root,"package",pkg); if(e==null) continue;
            String path=e.getAttribute("codePath");
            if(!path.startsWith("/data/app/")) continue;
            File from=new File(path).getCanonicalFile();
            if(!from.isDirectory() || !from.getName().startsWith(pkg+"-") || !from.getParentFile().getParent().equals("/data/app")) throw new IOException("更新包路径校验失败："+pkg);
            if(pkg.equals("com.lbe.security.miui")&&active(u,wanted)&&
                    new File(from,"base.apk").isFile()&&hash(new File(from,"base.apk")).equals(hash(new File(mod,"payload/AuthManager/AuthManager.apk"))))continue;
            File dest=state("detached-updates/"+pkg); if(dest.exists()) remove(dest); dest.getParentFile().mkdirs();
            Files.move(from.toPath(),dest.toPath()); moved.put(new JSONObject().put("package",pkg).put("from",from.getPath()));
        }
            if(!nativeAddons)tsmIdentity(enabled(wanted,"tsm"),settings);
            Files.move(current.toPath(),old.toPath());
            try {Files.move(next.toPath(),current.toPath());} catch(Exception e){Files.move(old.toPath(),current.toPath());throw e;}
            properties(wanted); job.put("updates",moved); write(state("mounted.json"),job);
            Files.delete(marker.toPath());
        } catch(Exception e) {
            if(identityChange && state("tsm-last-transition/packages.xml").isFile())rollbackTsm(originalTsm);
            if(old.exists() && !new File(mod,"next-job.json").exists())throw e; // committed generation; late() can finish
            if(old.exists()){if(current.exists())Files.move(current.toPath(),next.toPath(),StandardCopyOption.REPLACE_EXISTING);Files.move(old.toPath(),current.toPath());}
            for(int i=0;i<moved.length();i++){JSONObject v=moved.getJSONObject(i);File to=new File(v.getString("from"));to.getParentFile().mkdirs();Files.move(state("detached-updates/"+v.getString("package")).toPath(),to.toPath());}
            Files.deleteIfExists(state("mounted.json").toPath());
            properties(job.getJSONObject("before"));
            throw e;
        }
    }
    void migrateLauncher(boolean cn) throws Exception {
        String old=cn?POCO:HOME, target=cn?HOME:POCO;
        File archive=state("app-data/"+old+"/data.tar"); if(!archive.isFile()) return;
        File temp=state("launcher-transition"); if(temp.exists()) remove(temp); temp.mkdirs();
        cmd("/system/bin/tar","-xpf",archive.getPath(),"-C",temp.getPath());
        shell("am force-stop "+target+" >/dev/null 2>&1 || true");
        for(String base:Arrays.asList("/data/user/0/","/data/user_de/0/")) {
            File from=new File(temp,base.substring(1)+old), to=new File(base+target); if(!from.isDirectory()||!to.isDirectory()) continue;
            String owner=cmd("/system/bin/stat","-c","%u:%g",to.getPath());
            cmd("/system/bin/cp","-a",from.getPath()+"/.",to.getPath()+"/");
            File pref=new File(to,"shared_prefs/"+old+"_preferences.xml"); if(pref.isFile()) Files.copy(pref.toPath(),new File(to,"shared_prefs/"+target+"_preferences.xml").toPath(),StandardCopyOption.REPLACE_EXISTING);
            cmd("/system/bin/chown","-R",owner,to.getPath());cmd("/system/bin/restorecon","-RF",to.getPath());
        }
        remove(temp);cmd("/system/bin/cmd","package","set-home-activity","--user","0",target+(cn?"/com.miui.home.launcher.Launcher":"/com.miui.home.launcher.Launcher"));
    }
    String bootId() throws IOException {
        return new String(Files.readAllBytes(new File("/proc/sys/kernel/random/boot_id").toPath()),"UTF-8").trim();
    }
    void appliedResult() throws Exception {
        JSONObject result=new JSONObject().put("ok",true).put("phase","applied")
                .put("message","配置已在本次启动生效").put("time",System.currentTimeMillis());
        if(nativeAddons)result.put("bootId",bootId());
        write(state("last-result.json"),result);
    }
    void verifyLoaded(JSONObject wanted) throws Exception {
        if(nativeAddons) {
            JSONArray compat=catalog.optJSONArray("vendorCompat");
            if(compat!=null)for(int i=0;i<compat.length();i++) {
                JSONObject file=compat.getJSONObject(i);String path=file.getString("path");
                if(!(path.equals("/vendor/etc/init/qesdk-manager.rc")||path.equals("/vendor/etc/seccomp_policy/qesdk.policy")))
                    throw new IOException("QESDK 配置路径无效");
                File mounted=new File(path);
                if(!mounted.isFile()||!hash(mounted).equals(file.getString("sha256")))
                    throw new IOException("QESDK 配置未恢复，请检查 qesdk-compat.txt");
            }
            boolean cn=enabled(wanted,"cn_region");
            String[][] expected={{"ro.product.mod_device",cn?"ishtar":"ishtar_elite_global"},
                    {"ro.miui.region",cn?"CN":"HK"},{"ro.vendor.miui.region",cn?"CN":"HK"},
                    {"ro.miui.cust_variant",cn?"cn":"hk"},{"ro.vendor.miui.cust_variant",cn?"cn":"hk"}};
            for(String[] prop:expected)if(!prop[1].equals(cmd("/system/bin/getprop",prop[0])))
                throw new IOException("地区配置未正确应用："+prop[0]);
            File variant=new File("/data/miui/cust_variant");
            if(variant.isFile()&&!(cn?"cn":"hk").equals(new String(Files.readAllBytes(variant.toPath()),"UTF-8").trim()))
                throw new IOException("地区配置文件未正确挂载");
        }
        for(int i=0;i<units.length();i++) {
            JSONObject u=units.getJSONObject(i);if(!active(u,wanted))continue;
            if(cmd("/system/bin/pm","path",u.getString("package")).isEmpty())
                throw new IOException("组件未被系统加载："+u.getString("package"));
            // A store update can remain registered even when the module mount is
            // missing. Verify the base APK, libraries and grants as well; .replace
            // is a mount-manager marker and intentionally absent from /system.
            if(nativeAddons) {
                JSONArray files=u.getJSONArray("files");
                for(int k=0;k<files.length();k++) {
                    JSONObject file=files.getJSONObject(k);String path=file.getString("path");safe(mod,path);
                    if(!path.startsWith("system/")||path.endsWith("/.replace"))continue;
                    File mounted=new File("/"+path);
                    if(!mounted.isFile()||!hash(mounted).equals(file.getString("sha256")))
                        throw new IOException("组件文件未正确挂载："+u.getString("package")+" / "+mounted.getName());
                }
            }
        }
        if(nativeAddons && catalog.has("nativeFiles")) {
            JSONArray files=catalog.getJSONArray("nativeFiles");
            for(int i=0;i<files.length();i++) {
                JSONObject file=files.getJSONObject(i);if(!enabled(wanted,file.getString("feature")))continue;
                String path=file.getString("path");safe(mod,path);File mounted=new File("/"+path);
                if(!path.startsWith("system/etc/permissions/")||!mounted.isFile()||!hash(mounted).equals(file.getString("sha256")))
                    throw new IOException("原系统权限配置未正确挂载");
            }
        }
    }
    void late() throws Exception {
        if(!state("mounted.json").isFile()) {
            if(nativeAddons) {
                // Do not clear an early failure or a still-pending transaction.
                if(new File(mod,"next-job.json").isFile())throw new IOException("启动配置尚未切换，请查看运行详情后重试");
                if(!state("applied.json").isFile())throw new IOException("已应用配置缺失，请重新安装完整模块");
                if(!"RUNNING_UNLOCKED".equals(cmd("/system/bin/cmd","activity","get-started-user-state","0")))
                    throw new IOException("等待解锁以检查启动配置");
                verifyLoaded(config("applied.json"));
                appliedResult();
            }
            return;
        }
        JSONObject job=read(state("mounted.json")),wanted=job.getJSONObject("config"),before=job.getJSONObject("before");
        if(job.has("downloadSnapshot")) {
            File ready=state("downloads-ready-"+job.getLong("id")+".json");
            for(int i=0;i<200&&!ready.isFile();i++)Thread.sleep(100);
            if(!ready.isFile())throw new IOException("下载数据库解锁迁移尚未完成，请查看下载迁移日志");
        }
        // Wait for CE availability; service.sh calls this after boot, not post-fs.
        if(!"RUNNING_UNLOCKED".equals(cmd("/system/bin/cmd","activity","get-started-user-state","0"))) throw new IOException("等待解锁以完成应用数据恢复");
        JSONArray backups=job.getJSONArray("backups"), restores=job.getJSONArray("restores");
        Set<String> installed=new HashSet<>(Arrays.asList(cmd("/system/bin/pm","list","packages").split("\n")));
        for(int pass=0;pass<2;pass++) {
            JSONArray list=pass==0?backups:restores;
            for(int i=0;i<list.length();i++) {
                String pkg=list.getString(i), key="restored-"+pass+"-"+pkg;if(job.optBoolean(key))continue;
                // DownloadProvider keeps its UID and data. Replaying its outgoing
                // archive here would undo the pre-scan database version migration.
                if(pkg.equals("com.android.providers.downloads")) { /* preserved in place */ }
                else if(installed.contains("package:"+pkg))restore(pkg);
                else if(pass==1)throw new IOException("待恢复数据的组件未加载："+pkg);
                job.put(key,true);write(state("mounted.json"),job);
            }
        }
        if(!nativeAddons && state("tsm-finish").isFile()) {
            String uid=new String(Files.readAllBytes(state("tsm-finish").toPath()),"UTF-8").trim();
            for(String base:Arrays.asList("/data/user/0/","/data/user_de/0/")) if(new File(base+TSM).isDirectory()) {cmd("/system/bin/chown","-R",uid+":"+uid,base+TSM);cmd("/system/bin/restorecon","-RF",base+TSM);}
            Files.delete(state("tsm-finish").toPath());
        }
        if(!nativeAddons && enabled(before,"cloud")!=enabled(wanted,"cloud")&&!job.optBoolean("launcherDone")) {migrateLauncher(enabled(wanted,"cloud"));job.put("launcherDone",true);write(state("mounted.json"),job);}
        if(!enabled(before,"gallery")&&enabled(wanted,"gallery"))Files.deleteIfExists(state("gallery-sync-v1").toPath());
        if(!enabled(before,"cloud")&&enabled(wanted,"cloud"))Files.deleteIfExists(state("finddevice-location-v1").toPath());
        if(!nativeAddons && any(wanted)) {
            String path=cmd("/system/bin/pm","path","com.lbe.security.miui");
            File auth=new File(mod,"payload/AuthManager/AuthManager.apk");
            String current=path.replace("package:","").split("\n")[0];
            if(!hash(new File(current)).equals(hash(auth))) {
                String result=shell("pm install -r --user 0 -S "+auth.length()+" < "+quote(auth.getPath()));
                if(!result.contains("Success")) throw new IOException("国行权限管理器安装失败");
            }
        }
        if(!nativeAddons && !enabled(wanted,"clock")) {
            // The Global ROM's data-app preload is not rescanned after setup.
            // Register the original as an update as well, so uninstalling this
            // module after an all-off boot does not remove the only clock APK.
            JSONArray fallback=catalog.getJSONArray("clockFallback");
            File apk=null;for(int i=0;i<fallback.length();i++) {
                String path=fallback.getJSONObject(i).getString("path");
                if(path.endsWith(".apk")){apk=safe(mod,path);break;}
            }
            if(apk==null)throw new IOException("缺少国际版时钟回退包");
            String current=cmd("/system/bin/pm","path","com.android.deskclock").replace("package:","").split("\n")[0];
            if(!current.startsWith("/data/app/")) {
                String result=shell("pm install -r --user 0 -S "+apk.length()+" < "+quote(apk.getPath()));
                if(!result.contains("Success"))throw new IOException("国际版时钟登记失败");
            }
        }
        verifyLoaded(wanted);
        File previous=new File(mod,"previous-system");
        for(int i=0;i<units.length();i++) {
            JSONObject u=units.getJSONObject(i); boolean retain=!active(u,wanted)&&keep(u,wanted); JSONArray files=u.getJSONArray("files");
            for(int k=0;k<files.length();k++) {
                String path=files.getJSONObject(k).getString("path");if(!path.startsWith("system/"))continue;
                File held=safe(state("retained"),path), old=new File(previous,path.substring(7));
                if(retain && old.isFile()){held.getParentFile().mkdirs();Files.move(old.toPath(),held.toPath(),StandardCopyOption.REPLACE_EXISTING);}
                else if(!retain && held.isFile()) Files.delete(held.toPath());
            }
            File update=state("detached-updates/"+u.getString("package"));
            if(update.isDirectory()) {
                File kept=state("retained-updates/"+u.getString("package"));
                if(retain){if(kept.exists())remove(kept);kept.getParentFile().mkdirs();Files.move(update.toPath(),kept.toPath());}else remove(update);
            }
            if(!active(u,wanted)&&!retain) {File kept=state("retained-updates/"+u.getString("package"));if(kept.exists())remove(kept);}
            if(active(u,wanted)&&!active(u,before)) {
                File updateKept=state("retained-updates/"+u.getString("package"));
                if(updateKept.isDirectory()) {
                    File[] apks=updateKept.listFiles((d,n)->n.endsWith(".apk"));
                    if(apks!=null&&apks.length>0) {
                        File publicDir=new File("/data/local/tmp/songyuan-update");if(publicDir.exists())remove(publicDir);publicDir.mkdirs();publicDir.setReadable(true,false);publicDir.setExecutable(true,false);
                        long total=0;for(File apk:apks)total+=apk.length();
                        String created=cmd("/system/bin/pm","install-create","-r","--user","0","-S",Long.toString(total));
                        Matcher sessionMatch=Pattern.compile("\\[([0-9]+)\\]").matcher(created);
                        if(!sessionMatch.find())throw new IOException("无法创建更新恢复会话");
                        String session=sessionMatch.group(1);boolean committed=false;
                        try {
                            for(File apk:apks) {File copy=new File(publicDir,apk.getName());Files.copy(apk.toPath(),copy.toPath());copy.setReadable(true,false);}
                            cmd("/system/bin/restorecon","-RF",publicDir.getPath());
                            for(File apk:apks) {
                                File copy=new File(publicDir,apk.getName());
                                cmd("/system/bin/pm","install-write","-S",Long.toString(copy.length()),session,copy.getName(),copy.getPath());
                            }
                            String result=cmd("/system/bin/pm","install-commit",session);
                            if(!result.contains("Success"))throw new IOException("保留的商店更新恢复失败："+u.getString("package"));
                            committed=true;
                        } finally {if(!committed)shell("pm install-abandon "+session+" >/dev/null 2>&1 || true");remove(publicDir);}
                        remove(updateKept);
                    }
                }
            }
        }
        if(previous.exists())remove(previous);
        write(state("applied.json"),wanted); Files.delete(state("mounted.json").toPath());
        Files.deleteIfExists(state("config-early.txt").toPath());
        appliedResult();
    }
    JSONObject aiSearchStatus() {
        JSONObject status=new JSONObject();
        try {
            JSONObject policy=catalog.getJSONObject("aiSearch");
            String paths=cmd("/system/bin/pm","path",policy.getString("package"));
            if(!paths.startsWith("package:")||paths.contains("\n"))throw new IOException("AI 引擎版本需要重新适配");
            if(!policy.getString("apkSHA256").equals(hash(new File(paths.substring(8)))))
                throw new IOException("AI 引擎已更新，当前版本尚未适配");
            status.put("supported",true);
            String output=cmd("/system/bin/content","call","--user","0","--uri","content://com.dalong.cnbridge.config","--method","status");
            Matcher encoded=Pattern.compile("encoded=([A-Za-z0-9+/=]+)").matcher(output);
            if(!output.contains("ok=true")||!encoded.find())throw new IOException("请在 LSPosed 中启用国内化按应用适配与推荐作用域");
            JSONObject remote=new JSONObject(new String(Base64.getDecoder().decode(encoded.group(1)),"UTF-8"));
            if(remote.optInt("api")<102||remote.optInt("version")<policy.getInt("companionVersion")||!remote.optBoolean("aiSearchScopeReady"))
                throw new IOException("请更新配套 APK 并启用推荐作用域");
            status.put("ready",true).put("message","适配作用域已就绪 · 分析进度请到 AI 搜索查看");
        }catch(Exception e){try{status.put("ready",false).put("message",e.getMessage());}catch(Exception ignored){}}
        return status;
    }
    JSONObject status() throws Exception {
        JSONObject desired=config("desired.json"),applied=config("applied.json"),actual=new JSONObject();
        JSONArray displayUnits=new JSONArray(),displayFeatures=new JSONArray();
        for(int i=0;i<units.length();i++)displayUnits.put(units.getJSONObject(i));
        for(int i=0;i<features.length();i++)displayFeatures.put(features.getJSONObject(i));
        if(nativeAddons) {
            JSONArray nativeFeatures=catalog.getJSONArray("nativeFeatures");
            for(int i=0;i<nativeFeatures.length();i++) {
                JSONObject feature=nativeFeatures.getJSONObject(i);displayFeatures.put(feature);
                JSONArray packages=feature.getJSONArray("packages");
                for(int k=0;k<packages.length();k++)displayUnits.put(new JSONObject().put("package",packages.getString(k)).put("owners",new JSONArray().put(feature.getString("id"))));
            }
        }
        String list=cmd("/system/bin/pm","list","packages","-f","--show-versioncode");
        Pattern p=Pattern.compile("^package:(.*)=([^=\\s]+)(?:\\s+versionCode:([0-9]+))?$",Pattern.MULTILINE);Matcher m=p.matcher(list);
        while(m.find()) for(int i=0;i<displayUnits.length();i++) if(displayUnits.getJSONObject(i).getString("package").equals(m.group(2)))
            actual.put(m.group(2),new JSONObject().put("installed",true).put("updated",m.group(1).startsWith("/data/app/")).put("versionCode",m.group(3)));
        JSONObject featureIcons=new JSONObject();
        try {
            if(android.os.Looper.getMainLooper()==null)android.os.Looper.prepareMainLooper();
            Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
            android.content.Context context=(android.content.Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
            android.content.pm.PackageManager pm=context.getPackageManager();
            for(int i=0;i<displayFeatures.length();i++) {
                String id=displayFeatures.getJSONObject(i).getString("id");
                for(int k=0;k<displayUnits.length();k++) {
                    JSONObject unit=displayUnits.getJSONObject(k);if(!unit.getJSONArray("owners").optString(0).equals(id))continue;
                    String pkg=unit.getString("package");if(!actual.has(pkg))continue;
                    try {
                        android.content.pm.PackageInfo info=pm.getPackageInfo(pkg,0);
                        File cached=state("icons/"+pkg+"-"+info.getLongVersionCode()+"-"+info.lastUpdateTime+".png");
                        if(!cached.isFile()) {
                            cached.getParentFile().mkdirs();
                            android.graphics.drawable.Drawable drawable=pm.getApplicationIcon(pkg);
                            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(96,96,android.graphics.Bitmap.Config.ARGB_8888);
                            drawable.setBounds(0,0,96,96);drawable.draw(new android.graphics.Canvas(bitmap));
                            try(FileOutputStream out=new FileOutputStream(cached)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
                            bitmap.recycle();
                        }
                        featureIcons.put(id,"data:image/png;base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(cached.toPath())));break;
                    }catch(Exception ignored){/* retain the built-in fallback for an unavailable icon */}
                }
            }
        }catch(Exception ignored){/* status remains available if Android icon rendering is unavailable */}
        boolean reboot=new File(mod,"next-job.json").isFile(),applying=state("mounted.json").isFile();
        JSONObject result=state("last-result.json").isFile()?read(state("last-result.json")):new JSONObject();
        JSONObject response=new JSONObject().put("ok",true).put("desired",desired).put("applied",applied).put("actual",actual).put("icons",featureIcons)
                .put("pending",reboot).put("applying",applying)
                .put("result",result).put("bootVerified",!nativeAddons||bootId().equals(result.optString("bootId")))
                .put("version",catalog.getString("version")).put("sourceAvailable",state("source.zip").isFile());
        if(!nativeAddons&&catalog.has("aiSearch"))response.put("aiSearch",aiSearchStatus());
        return response;
    }
    public static void main(String[] args) {
        try {
            if(args.length<2)throw new IOException("缺少操作参数");
            File candidate=new File(args[0]).getCanonicalFile();
            if(!(candidate.getParent().equals("/data/adb/modules")||candidate.getParent().equals("/data/adb/modules_update")))throw new IOException("模块路径无效");
            if(candidate.getName().equals("ishtar_cn_addons"))STATE=new File("/data/adb/ishtar_cn");
            else if(!candidate.getName().equals("songyuan_cn_complete"))throw new IOException("模块路径无效");
            STATE.mkdirs(); cmd("/system/bin/chmod","700",STATE.getPath());
            ComponentController c=new ComponentController(args[0]);String op=args[1];
            if(op.equals("unlock-downloads")){if(args.length!=3)throw new IOException("缺少下载迁移编号");c.unlockDownloads(args[2]);return;}
            try(RandomAccessFile lock=new RandomAccessFile(c.state("config.lock"),"rw");FileLock held=lock.getChannel().lock()) {
                if(op.equals("status")){System.out.println(c.status());return;}
                if(op.equals("save")) {
                    if(args.length!=3||args[2].length()>16384||!args[2].matches("[A-Za-z0-9+/=]+"))throw new IOException("配置编码无效");
                    c.prepare(c.validate(new JSONObject(new String(Base64.getDecoder().decode(args[2]),"UTF-8"))),false,false);
                } else if(op.equals("install")) {
                    if(c.nativeAddons) {
                        if(!c.state("applied.json").isFile())c.requireNativePackagesAbsent();
                        String libraries=cmd("/system/bin/pm","list","libraries");
                        Set<String> available=new HashSet<>(Arrays.asList(libraries.split("\n")));
                        JSONArray required=c.catalog.getJSONArray("requiredLibraries");
                        for(int i=0;i<required.length();i++)if(!available.contains("library:"+required.getString(i)))throw new IOException("当前 ROM 缺少必需共享库："+required.getString(i));
                    }
                    JSONObject wanted=c.config("desired.json");
                    boolean upgrading=c.state("applied.json").isFile();
                    if(!upgrading) {
                        JSONObject original=c.defaults();Iterator<String> ids=original.keys();
                        while(ids.hasNext())original.getJSONObject(ids.next()).put("enabled",false);
                        write(c.state("applied.json"),original);
                    }
                    // The installer archive contains every CN APK. Make its current
                    // tree match the applied configuration first, so a rejected
                    // pre-scan migration cannot accidentally mount the new CN tree.
                    c.prepare(c.config("applied.json"),false,upgrading);
                    File current=new File(c.mod,"system");if(current.exists())remove(current);
                    Files.move(new File(c.mod,"next-system").toPath(),current.toPath());
                    Files.delete(new File(c.mod,"next-job.json").toPath());
                    c.properties(c.config("applied.json"));
                    write(c.state("desired.json"),wanted);
                    c.prepare(wanted,true,upgrading);
                }
                else if(op.equals("early"))c.early();
                else if(op.equals("late"))c.late();
                else throw new IOException("不支持的操作");
                System.out.println(new JSONObject().put("ok",true).put("message","操作完成"));
            }
        } catch(Exception e) {
            try{JSONObject error=new JSONObject().put("ok",false).put("message",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()).put("phase",args.length>1?args[1]:"error");
                if(args.length>1 && (args[1].equals("early")||args[1].equals("late")))write(new File(STATE,"last-result.json"),error);
                System.out.println(error);
            }catch(Exception ignored){System.out.println("{\"ok\":false,\"message\":\"配置操作失败\"}");}
            System.exit(1);
        }
    }
}
