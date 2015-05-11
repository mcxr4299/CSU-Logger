package xyz.codeme.szzn.http;

import java.net.HttpURLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

import xyz.codeme.loginer.LoginFragment;
import xyz.codeme.loginer.MainActivity;
import xyz.codeme.loginer.R;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Resources;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

/**
 * 数字中南登陆网络交互类
 * <p/>
 * 首先获取IP，根据用户名/加密后的密码/IP，可登陆/下线/重新登陆(先下线再登陆)。
 * session为内部管理，过期后重登即可重新获取。
 * 登陆操作后可获取账户信息。
 *
 * @author Msir
 * @date 2015/3
 */
public class HttpUtils {
    public final static int IP_FROM_SERVER = 0xac01;
    public final static int IP_FROM_ROUTER = 0xac02;

    private final static String loginUrl = "http://61.137.86.87:8080/portalNat444/AccessServices/login";
    private final static String logoutUrl = "http://61.137.86.87:8080/portalNat444/AccessServices/logout";
    private final static String mainUrl = "http://61.137.86.87:8080/portalNat444/index.jsp";
    private final static String showUrl = "http://61.137.86.87:8080/portalNat444/main2.jsp";
    // 192.168.56.1/sz/ szzn.sinaapp.com/ codeme.xyz/api/
    private final static String saveUrl = "http://codeme.xyz/api/saveip.php";
    private final static String getipUrl = "http://codeme.xyz/api/getip.php";

    private final LoginFragment fragment;
    private RequestQueue requestQueue;

    private int ipAccessMethod = IP_FROM_SERVER;
    private String routerURL = "";
    private String routerReferer = "";
    private String routerCookie = "";
    private String routerReg = "";
    private String session = "";
    private String log = "";
    private String user;
    private String password;
    private String ip;
    private boolean ifConnected = false;
    private boolean ifSaveIP = true;

    public HttpUtils(LoginFragment fragment) {
        this.fragment = fragment;
        this.requestQueue = Volley.newRequestQueue(fragment.getActivity());
        HttpURLConnection.setFollowRedirects(false);
        this.refreshSession(false);
    }

    /**
     * 路由器配置
     *
     * @param routerURL     路由管理页
     * @param routerReferer 路由管理头部Referrer
     * @param routerCookie  路由登陆Cookie
     * @param routerReg     匹配IP地址正则表达式
     */
    public void routerConfigure(String routerURL, String routerReferer,
                                String routerCookie, String routerReg) {
        ipAccessMethod = IP_FROM_ROUTER;
        this.routerURL = routerURL;
        this.routerReferer = routerReferer;
        this.routerCookie = routerCookie;
        this.routerReg = routerReg;
    }

    /**
     * 登陆
     */
    public void login(final String user, final String password, final String IP) {
        KeyValuePairs form = KeyValuePairs.create()
                .add("accountID", user + "@zndx.inter")
                .add("password", password)
                .add("brasAddress", "59df7586")
                .add("userIntranetAddress", IP);
        KeyValuePairs headers = KeyValuePairs.create()
                .add("Referer", HttpUtils.mainUrl)
                .add("Cookie", "JSESSIONID=" + this.session);

        final ProgressDialog progressDialog;
        progressDialog = ProgressDialog.show(fragment.getActivity(), "Loading...", "正在登陆");

        FluentJsonRequest jsonRequest = new FluentJsonRequest(
                HttpUtils.loginUrl,
                form.build(),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObj) {
                        try {
                            int resultCode = jsonObj.getInt("resultCode");
                            if (resultCode == 0) {
                                log = parseCode(resultCode);
                                ifConnected = true;
                                showToast(R.string.success_login);
                                Log.i(MainActivity.TAG, "login:success");
                                if (session.length() > 0)
                                    getInformation();
                                if(ifSaveIP)
                                    saveIP(user, IP);
                                fragment.saveForm();
                                return;
                            }
                            log = parseCode(resultCode) + " " + jsonObj.getString("resultDescribe");
                            showMessage(R.string.error_login, log);
                            Log.e(MainActivity.TAG, "login:" + log);
                        } catch (JSONException e) {
                            Log.e(MainActivity.TAG, "login:json error");
                            showToast(R.string.error_login);
                        } finally {
                            if (progressDialog.isShowing())
                                progressDialog.dismiss();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError arg0) {
                        if (progressDialog.isShowing())
                            progressDialog.dismiss();
                        showToast(R.string.error_login);
                        Log.e(MainActivity.TAG, "login:connect error");
                    }
                });
        jsonRequest.setHeaders(headers.build());

        requestQueue.add(jsonRequest);
    }

    /**
     * 下线
     *
     * @param IP 需下线IP，任何IP均可
     */
    public void logout(final String IP) {
        KeyValuePairs form = KeyValuePairs.create()
                .add("brasAddress", "59df7586")
                .add("userIntranetAddress", IP);
        KeyValuePairs headers = KeyValuePairs.create()
                .add("Referer", HttpUtils.showUrl);

        final ProgressDialog progressDialog;
        progressDialog = ProgressDialog.show(fragment.getActivity(), "Loading...", "正在下线");

        FluentJsonRequest jsonRequest = new FluentJsonRequest(
                HttpUtils.logoutUrl,
                form.build(),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObj) {
                        try {
                            int resultCode = jsonObj.getInt("resultCode");
                            if (resultCode == 0) {
                                log = parseCode(resultCode);
                                ifConnected = false;
                                showToast(R.string.success_logout);
                                Log.i(MainActivity.TAG, "logout:success");
                                return;
                            }
                            log = parseCode(resultCode) + " " + jsonObj.getString("resultDescribe");
                            showMessage(R.string.error_logout, log);
                            Log.e(MainActivity.TAG, "logout:" + log);
                        } catch (JSONException e) {
                            Log.e(MainActivity.TAG, "logout:json error");
                            showToast(R.string.error_logout);
                        } finally {
                            if (progressDialog.isShowing())
                                progressDialog.dismiss();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError arg0) {
                        if (progressDialog.isShowing())
                            progressDialog.dismiss();
                        showToast(R.string.error_logout);
                        Log.e(MainActivity.TAG, "logout:connect error");
                    }
                });
        jsonRequest.setHeaders(headers.build());

        requestQueue.add(jsonRequest);
    }

    /**
     * 重新登陆
     */
    public void relogin(String user, String password, String ip) {
        refreshSession(true);
        this.user = user;
        this.password = password;
        this.ip = ip;
    }

    /**
     * 获取IP地址
     */
    public void getIP() {
        String ip = null;
        WifiManager wifiManager;
        wifiManager = (WifiManager) fragment.getActivity().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int i = wifiInfo.getIpAddress();
            if ((i & 0xFF) == 10 && ((i >> 8) & 0xFF) == 96) {
                ip = "10.96." + ((i >> 16) & 0xFF) + "." + ((i >> 24) & 0xFF);
            }
        } else {
            showToast(R.string.error_nowifi);
            return;
        }
        if (ip != null) {
            fragment.showIP(ip);
            return;
        }
        Log.w(MainActivity.TAG, "local端获取IP失败");
        switch (this.ipAccessMethod) {
            case IP_FROM_SERVER:
                getIPFromServer();
                break;
            case IP_FROM_ROUTER:
                getIPFromRouter();
                break;
        }
    }

    /**
     * 上传保存IP
     *
     * @param user 用户名
     * @param IP   要保存的IP
     */
    public void saveIP(String user, String IP) {
        KeyValuePairs form = KeyValuePairs.create()
                .add("user", user)
                .add("ip", IP);

        FluentJsonRequest jsonRequest = new FluentJsonRequest(
                HttpUtils.saveUrl,
                form.build(),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObj) {
                        try {
                            int resultCode = jsonObj.getInt("code");
                            if (resultCode != 0) {
                                log = jsonObj.getInt("code") + ":" + jsonObj.getString("msg");
                                Log.w(MainActivity.TAG, "saveip:" + log);
                            }
                        } catch (JSONException e) {
                            Log.e(MainActivity.TAG, "saveip:json error");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError arg0) {
                        Log.e(MainActivity.TAG, "saveip:connect error");
                    }
                });

        requestQueue.add(jsonRequest);
    }

    /**
     * 获取上次登录IP
     *
     * @param user 要获取IP的用户
     */
    public void getLastIP(String user) {
        KeyValuePairs form = KeyValuePairs.create()
                .add("user", user);

        final ProgressDialog progressDialog;
        progressDialog = ProgressDialog.show(fragment.getActivity(), "Loading...", "正在获取");

        FluentJsonRequest jsonRequest = new FluentJsonRequest(
                HttpUtils.getipUrl,
                form.build(),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObj) {
                        try {
                            int resultCode = jsonObj.getInt("code");
                            if (resultCode == 0) {
                                String ip = jsonObj.getString("ip");
//                				String time = jsonObj.getString("time");
                                fragment.showIP(ip);
                            } else {
                                log = jsonObj.getInt("code") + ":" + jsonObj.getString("msg");
                                Log.e(MainActivity.TAG, "getlastip:" + log);
                                showMessage(R.string.error_ip, log);
                            }
                        } catch (JSONException e) {
                            Log.e(MainActivity.TAG, "getlastip:json error");
                            showToast(R.string.error_ip);
                        } finally {
                            if (progressDialog.isShowing())
                                progressDialog.dismiss();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError arg0) {
                        if (progressDialog.isShowing())
                            progressDialog.dismiss();
                        Log.e(MainActivity.TAG, "getlastip:connect error");
                        showToast(R.string.error_ip);
                    }
                });

        requestQueue.add(jsonRequest);
    }

    /**
     * 获取用户信息
     */
    public void getInformation() {
        KeyValuePairs headers = KeyValuePairs.create()
                .add("Referer", HttpUtils.mainUrl)
                .add("Cookie", "JSESSIONID=" + this.session);

        FluentStringRequest request = new FluentStringRequest(
                Request.Method.GET,
                HttpUtils.showUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String content) {
                        AccountInfo account = new AccountInfo(content);
                        fragment.showAccountInformation(account);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError arg0) {
                        showToast(R.string.error_account);
                    }
                });
        request.setHeaders(headers.build());
        requestQueue.add(request);
    }

    /**
     * 从数字中南获取IP
     */
    private void getIPFromServer() {
        FluentStringRequest request = new FluentStringRequest(
                Request.Method.GET,
                "http://wap.baidu.com/",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String content) {
                        if (content.indexOf("百度") > 0) // 当前在线
                        {
                            ifConnected = true;
                            showToast(R.string.msg_online);
                            Log.w(MainActivity.TAG, "server端获取IP失败，在线");
                            return;
                        }

                        Pattern pattern = Pattern.compile("10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
                        Matcher match = pattern.matcher(content);
                        if (match.find()) {
                            fragment.showIP(match.group(0));
                            Log.i(MainActivity.TAG, "getIPFromServer:success");
                        } else {
                            Log.w(MainActivity.TAG, "server端获取IP失败");
                            showToast(R.string.error_ip);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError arg0) {
                        Log.w(MainActivity.TAG, "server端获取IP失败");
                        showToast(R.string.error_ip);
                    }
                });

        requestQueue.add(request);
    }

    /**
     * 根据配置从路由器web管理页面获取当前IP
     */
    private void getIPFromRouter() {
        KeyValuePairs headers = KeyValuePairs.create()
                .add("Referer", this.routerReferer)
                .add("Cookie", this.routerCookie);
        FluentStringRequest request = new FluentStringRequest(
                Request.Method.GET,
                this.routerURL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String content) {
                        Pattern pattern = Pattern.compile(routerReg);
                        Matcher match = pattern.matcher(content);
                        if (match.find()) {
                            fragment.showIP(match.group(0));
                            Log.i(MainActivity.TAG, "getIPFromRouter:success");
                        } else {
                            Log.w(MainActivity.TAG, "router端获取IP失败");
                            showToast(R.string.error_router);
                            getIPFromServer();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError arg0) {
                        Log.w(MainActivity.TAG, "router端获取IP失败");
                        showToast(R.string.error_ip);
                    }
                });
        request.setHeaders(headers.build());
        requestQueue.add(request);
    }

    /**
     * 获取整个过程session
     */
    private void refreshSession(final boolean ifAnnounce) {
        FluentStringRequest request = new FluentStringRequest(
                Request.Method.GET,
                HttpUtils.mainUrl,
                true,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String headers) {
                        Pattern pattern = Pattern.compile("JSESSIONID=([^;]+);");
                        Matcher match = pattern.matcher(headers);
                        if (match.find()) {
                            session = match.group(1);
                            Log.i(MainActivity.TAG, "session refresh success");
                            if (ifAnnounce)
                                refreshSuccess();
                        } else
                            Log.w(MainActivity.TAG, "获取session失败");
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError arg0) {
                        Log.w(MainActivity.TAG, "获取session失败");
                    }
                });
        requestQueue.add(request);
    }

    private void refreshSuccess() {
        logout(ip);
        login(user, password, ip);
    }

    /**
     * 显示Toast
     *
     * @param resourceId 资源id
     */
    private void showToast(int resourceId) {
        Toast.makeText(fragment.getActivity(), resourceId, Toast.LENGTH_SHORT).show();
    }

    /**
     * 显示消息提示
     *
     * @param resourceId 资源id，标题
     * @param log        内容
     */
    private void showMessage(int resourceId, String log) {
        Resources res = fragment.getResources();
        new AlertDialog.Builder(fragment.getActivity())
                .setTitle(res.getString(resourceId))
                .setMessage(log)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    /**
     * 解析json中code含义
     *
     * @param code 返回结果码
     * @return 含义
     */
    private String parseCode(int code) {
        switch (code) {
            case 0:
                return "成功";
            case 1:
                return "其他原因认证拒绝";
            case 2:
                return "用户连接已经存在";
            case 3:
                return "接入服器务繁忙，稍后重试";
            case 4:
                return "未知错误";
            case 6:
                return "认证响应超时";
            case 7:
                return "捕获用户网络地址错误";
            case 8:
                return "服务器网络连接异常";
            case 9:
                return "认证服务脚本执行异常";
            case 10:
                return "校验码错误";
            case 11:
                return "您的密码相对简单，帐号存在被盗风险，请及时修改成强度高的密码";
            case 12:
                return "无法获取您的网络地址,请输入任意其它网站从网关处导航至本认证页面";
            case 13:
                return "无法获取您接入点设备地址，请输入任意其它网站从网关处导航至本认证页面";
            case 14:
                return "无法获取您套餐信息";
            case 16:
                return "请输入任意其它网站导航至本认证页面,并按正常PORTAL正常流程认证";
            case 17:
                return "连接已失效，请输入任意其它网站从网关处导航至本认证页面";
            default:
                return "未知错误";
        }
    }

    /**
     * 获取登陆/下线日志信息
     */
    public String getLog() {
        return log;
    }

    /**
     * 获取当前状态(可能不准确)
     */
    public boolean isIfConnected() {
        return ifConnected;
    }

    /**
     * 获取默认获取IP方式
     */
    public int getIpAccessMethod() {
        return ipAccessMethod;
    }

    /**
     * 修改默认获取IP的方式
     */
    public void setIpAccessMethod(int ipAccessMethod) {
        this.ipAccessMethod = ipAccessMethod;
    }

    public void setIfSaveIP(boolean saveIP) {
        this.ifSaveIP = saveIP;
    }
}