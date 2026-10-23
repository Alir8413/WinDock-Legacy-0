package com.windock.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.Context;
import android.content.SharedPreferences;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal Android 4.0-compatible Wi-Fi touchpad client for the existing WinDock host. */
public class MainActivity extends Activity {
    private static final int DISCOVERY_PORT = 41234;
    private static final String PROBE = "windock:discover:v1";
    private static final String REPLY = "windock:here:v1:";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String endpoint;
    private String cookie;
    private WebSocketClient socket;
    private TextView status;
    private View pad;
    private float lastX, lastY;
    private long lastMoveAt;
    private long downAt;
    private boolean dragging;
    private int textColor = Color.rgb(243,243,243);
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("windock-legacy", MODE_PRIVATE);
        endpoint = prefs.getString("endpoint", "");
        showConnectScreen("Ready. Connect your PC and phone to the same Wi-Fi.");
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextColor(textColor); v.setTextSize(size);
        v.setGravity(Gravity.CENTER); v.setPadding(dp(8),dp(8),dp(8),dp(8)); return v;
    }
    private Button button(String value) {
        Button b = new Button(this); b.setText(value); b.setTextSize(14); return b;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER); l.setPadding(dp(16),dp(12),dp(16),dp(12)); l.setBackgroundColor(Color.rgb(24,24,24)); return l;
    }
    private void showConnectScreen(String message) {
        LinearLayout root = column();
        root.addView(label("WinDock Legacy", 26));
        status = label(message, 14); root.addView(status);
        final EditText address = new EditText(this); address.setSingleLine(true); address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.LTGRAY); address.setHint("PC address, e.g. 192.168.1.20:8620"); address.setText(endpoint);
        root.addView(address, new LinearLayout.LayoutParams(-1, dp(52)));
        Button find = button("Find PC on Wi-Fi"); root.addView(find);
        Button connect = button("Connect"); root.addView(connect);
        find.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            status.setText("Searching local Wi-Fi…");
            worker.execute(new Runnable() { public void run() {
                final String found = discover();
                runOnUiThread(new Runnable() { public void run() {
                    if (found != null) { endpoint = found; address.setText(found); status.setText("PC found. Tap Connect."); }
                    else status.setText("PC not found. Enter its IP address and check Windows Firewall.");
                }});
            }});
        }});
        connect.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            String entered = address.getText().toString().trim();
            if (entered.length() == 0) { status.setText("Enter the PC address first."); return; }
            endpoint = normalize(entered);
            if (endpoint == null) { status.setText("Invalid address. Use a local IPv4 address, optionally with :port."); return; }
            askPin();
        }});
        setContentView(root);
    }

    private String normalize(String value) {
        String s = value.trim();
        if (!s.startsWith("http://")) s = "http://" + s;
        try {
            URI u = new URI(s); String host = u.getHost(); int port = u.getPort() < 0 ? 8620 : u.getPort();
            if (host == null || host.indexOf(':') >= 0 || port < 1 || port > 65535) return null;
            String[] octets = host.split("\\."); if (octets.length != 4) return null;
            for (String part : octets) { int n = Integer.parseInt(part); if (n < 0 || n > 255) return null; }
            int a = Integer.parseInt(octets[0]);
            if (!(a == 10 || (a == 192 && Integer.parseInt(octets[1]) == 168) || (a == 172 && Integer.parseInt(octets[1]) >= 16 && Integer.parseInt(octets[1]) <= 31) || a == 127)) return null;
            return "http://" + host + ":" + port;
        } catch (Exception e) { return null; }
    }

    private void askPin() {
        final EditText pin = new EditText(this); pin.setSingleLine(true); pin.setInputType(2); pin.setHint("4-digit PIN shown on Windows");
        new AlertDialog.Builder(this).setTitle("Pair with WinDock").setView(pin)
            .setNegativeButton("Cancel", null).setPositiveButton("Pair", (dialog, which) -> {
                final String value = pin.getText().toString().trim();
                if (!value.matches("[0-9]{4}")) { showConnectScreen("PIN must contain exactly 4 digits."); return; }
                status.setText("Pairing…");
                worker.execute(new Runnable() { public void run() {
                    final String error = pair(value);
                    runOnUiThread(new Runnable() { public void run() {
                        if (error != null) showConnectScreen(error); else { prefs.edit().putString("endpoint", endpoint).apply(); openTouchpad(); }
                    }});
                }});
            }).show();
    }

    private String pair(String pin) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(endpoint + "/api/auth"); c = (HttpURLConnection)url.openConnection();
            c.setConnectTimeout(4000); c.setReadTimeout(4000); c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setRequestProperty("Accept", "application/json");
            byte[] body = new JSONObject().put("pin", pin).toString().getBytes("UTF-8");
            OutputStream out = c.getOutputStream(); out.write(body); out.close();
            int code = c.getResponseCode();
            if (code != 200) return code == 401 ? "Wrong PIN. Check the PIN in the Windows tray." : "Pairing failed (HTTP " + code + "). Check the host address and firewall.";
            String setCookie = c.getHeaderField("Set-Cookie");
            if (setCookie == null || setCookie.indexOf("windock_session=") < 0) return "The PC did not return a WinDock session.";
            cookie = setCookie.split(";", 2)[0]; return null;
        } catch (Exception e) { return "Could not connect: " + e.getMessage(); }
        finally { if (c != null) c.disconnect(); }
    }

    private String discover() {
        DatagramSocket s = null;
        try {
            s = new DatagramSocket(null); s.setReuseAddress(true); s.bind(new InetSocketAddress(0)); s.setBroadcast(true); s.setSoTimeout(650);
            byte[] data = PROBE.getBytes("UTF-8"); InetAddress bcast = InetAddress.getByName("255.255.255.255");
            for (int i=0;i<3;i++) {
                s.send(new DatagramPacket(data, data.length, bcast, DISCOVERY_PORT));
                long until = System.currentTimeMillis() + 650;
                while (System.currentTimeMillis() < until) {
                    byte[] buf = new byte[256]; DatagramPacket p = new DatagramPacket(buf, buf.length);
                    try { s.receive(p); } catch (java.net.SocketTimeoutException e) { break; }
                    String reply = new String(p.getData(), 0, p.getLength(), "UTF-8");
                    if (reply.startsWith(REPLY)) {
                        String candidate = normalize(reply.substring(REPLY.length()).trim());
                        if (candidate != null) return "http://" + p.getAddress().getHostAddress() + ":" + new URI(candidate).getPort();
                    }
                }
            }
        } catch (Exception ignored) { } finally { if (s != null) s.close(); }
        return null;
    }

    private void openTouchpad() {
        LinearLayout root = column();
        TextView title = label("WinDock Touchpad", 20); root.addView(title);
        status = label("Connecting to PC…", 12); root.addView(status, new LinearLayout.LayoutParams(-1, dp(36)));
        pad = label("Touch here to move the mouse\nTap = left click · Long press = right click", 18);
        pad.setBackgroundColor(Color.rgb(40,40,40));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, 0, 1); pp.setMargins(0,dp(8),0,dp(8)); root.addView(pad, pp);
        Button back = button("Disconnect"); root.addView(back); back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { closeSocket(); showConnectScreen("Disconnected."); }});
        pad.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                float x=e.getX(), y=e.getY(); long now=System.currentTimeMillis();
                if (e.getAction()==MotionEvent.ACTION_DOWN) { lastX=x; lastY=y; lastMoveAt=now; downAt=now; dragging=false; return true; }
                if (e.getAction()==MotionEvent.ACTION_MOVE) {
                    int dx=Math.round((x-lastX)*1.7f), dy=Math.round((y-lastY)*1.7f);
                    if (Math.abs(dx)+Math.abs(dy)>0 && now-lastMoveAt>=12) { sendMove(dx,dy); lastX=x; lastY=y; lastMoveAt=now; dragging=true; }
                    return true;
                }
                if (e.getAction()==MotionEvent.ACTION_UP) {
                    if (!dragging) { if (now-downAt >= 550) sendButton("right", true, true); else sendButton("left", true, true); }
                    return true;
                }
                return true;
            }
        });
        setContentView(root); connectSocket();
    }

    private void connectSocket() {
        try {
            URI uri = new URI("ws" + endpoint.substring(4) + "/input");
            Map<String,String> headers = new HashMap<String,String>(); headers.put("Cookie", cookie);
            socket = new WebSocketClient(uri, new org.java_websocket.drafts.Draft_6455(), headers, 5000) {
                @Override public void onOpen(ServerHandshake handshake) { runOnUiThread(new Runnable(){ public void run(){ if(status!=null) status.setText("Connected. Keep both devices on the same Wi-Fi."); }}); }
                @Override public void onMessage(String message) { try { final JSONObject obj = new JSONObject(message); if ("error".equals(obj.optString("type"))) runOnUiThread(new Runnable(){ public void run(){ if(status!=null) status.setText(obj.optString("error", "Touchpad error")); }}); } catch (Exception ignored) {} }
                @Override public void onClose(int code, String reason, boolean remote) { runOnUiThread(new Runnable(){ public void run(){ if(status!=null) status.setText("Disconnected from PC."); }}); }
                @Override public void onError(Exception ex) { runOnUiThread(new Runnable(){ public void run(){ if(status!=null) status.setText("Touchpad connection failed. Update WinDock on PC."); }}); }
            };
            socket.connect();
        } catch (Exception e) { status.setText("Could not start touchpad: " + e.getMessage()); }
    }
    private void sendJson(String json) { try { if(socket!=null && socket.isOpen()) socket.send(json); } catch (Exception ignored) {} }
    private void sendMove(int dx, int dy) {
        if (dx == 0 && dy == 0) return; dx=Math.max(-2048,Math.min(2048,dx)); dy=Math.max(-2048,Math.min(2048,dy));
        sendJson("{\"type\":\"move\",\"dx\":"+dx+",\"dy\":"+dy+"}");
    }
    private void sendButton(String button, boolean down, boolean alsoUp) {
        sendJson("{\"type\":\"button\",\"button\":\""+button+"\",\"down\":"+down+"}");
        if (alsoUp) sendJson("{\"type\":\"button\",\"button\":\""+button+"\",\"down\":false}");
    }
    private void closeSocket() { try { if(socket!=null) socket.close(); } catch (Exception ignored) {} socket=null; }
    @Override public void onBackPressed() { if (pad != null && pad.getParent()!=null) { closeSocket(); pad=null; showConnectScreen("Disconnected."); } else super.onBackPressed(); }
    @Override protected void onDestroy() { closeSocket(); worker.shutdownNow(); super.onDestroy(); }
}
