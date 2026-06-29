package com.foobnix.wifitransfer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppProfile;
import com.foobnix.pdf.info.R;
import com.foobnix.ui2.BooksService;
import com.foobnix.work.SearchAllBooksWorker;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class WifiTransferActivity extends AppCompatActivity {

    private TextView ipAddressText;
    private TextView statusText;
    private Button startStopButton;
    private ImageView backButton;
    private WifiTransferServer server;
    private boolean isServerRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_transfer);

        ipAddressText = findViewById(R.id.ipAddressText);
        statusText = findViewById(R.id.statusText);
        startStopButton = findViewById(R.id.startStopButton);
        backButton = findViewById(R.id.backButton);

        String ip = getIPAddress();
        ipAddressText.setText("IP地址: " + ip);

        backButton.setOnClickListener(v -> {
            finish();
        });

        startStopButton.setOnClickListener(v -> {
            if (isServerRunning) {
                stopServer();
            } else {
                startServer();
            }
        });
    }

    private String getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.e(e, "获取IP地址失败");
        }
        return "192.168.1.100";
    }

    private void startServer() {
        File uploadDir = AppProfile.DOWNLOADS_DIR;
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        server = new WifiTransferServer(uploadDir, new WifiTransferServer.UploadCallback() {
            @Override
            public void onUploadSuccess(String fileName) {
                runOnUiThread(() -> {
                    Toast.makeText(WifiTransferActivity.this, "上传成功: " + fileName, Toast.LENGTH_SHORT).show();
                    scanNewBooks();
                });
            }

            @Override
            public void onUploadError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(WifiTransferActivity.this, "上传失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onServerStartError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(WifiTransferActivity.this, "服务启动失败: " + error, Toast.LENGTH_SHORT).show();
                    statusText.setText("服务未启动");
                    startStopButton.setText("启动服务");
                    isServerRunning = false;
                });
            }
        });

        server.start();
        isServerRunning = true;
        statusText.setText("服务运行中\n请在浏览器访问: http://" + getIPAddress() + ":18080\n书籍将保存到: " + uploadDir.getPath());
        startStopButton.setText("停止服务");
        Toast.makeText(this, "WLAN传书服务已启动", Toast.LENGTH_SHORT).show();
    }

    private void scanNewBooks() {
        SearchAllBooksWorker.run(getApplicationContext());
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
        }
        isServerRunning = false;
        statusText.setText("服务已停止");
        startStopButton.setText("启动服务");
        Toast.makeText(this, "WLAN传书服务已停止", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
    }
}