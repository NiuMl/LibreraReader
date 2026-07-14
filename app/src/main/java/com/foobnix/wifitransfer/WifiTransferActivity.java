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

/**
 * WLAN传书活动界面
 * <p>
 * 提供通过局域网浏览器上传书籍到设备的功能。用户可以启动一个本地HTTP服务器，
 * 在同一局域网内的浏览器中访问指定地址，通过拖拽或选择文件的方式上传书籍。
 * 上传完成后自动扫描新书并更新书库。
 */
public class WifiTransferActivity extends AppCompatActivity {

    /** IP地址显示文本框 */
    private TextView ipAddressText;
    /** 服务状态显示文本框 */
    private TextView statusText;
    /** 启动/停止服务按钮 */
    private Button startStopButton;
    /** 返回按钮 */
    private ImageView backButton;
    /** WiFi传输服务器实例 */
    private WifiTransferServer server;
    /** 服务器运行状态标志 */
    private boolean isServerRunning = false;

    /**
     * 活动创建时的初始化操作
     * <p>
     * 绑定界面控件，获取并显示设备IP地址，设置按钮点击事件监听。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_transfer);

        ipAddressText = findViewById(R.id.ipAddressText);
        statusText = findViewById(R.id.statusText);
        startStopButton = findViewById(R.id.startStopButton);
        backButton = findViewById(R.id.backButton);

        // 获取并显示设备IP地址
        String ip = getIPAddress();
        ipAddressText.setText(getString(R.string.ip_address) + ": " + ip);

        // 返回按钮点击事件
        backButton.setOnClickListener(v -> {
            finish();
        });

        // 启动/停止按钮点击事件
        startStopButton.setOnClickListener(v -> {
            if (isServerRunning) {
                stopServer();
            } else {
                startServer();
            }
        });
    }

    /**
     * 获取设备的IPv4地址
     * <p>
     * 遍历所有网络接口，查找非回环地址的IPv4地址。
     * 如果未找到，则返回默认地址 "192.168.1.100"。
     *
     * @return 设备的IPv4地址字符串
     */
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
            LOG.e(e, "Failed to get IP address");
        }
        return "192.168.1.100";
    }

    /**
     * 启动WiFi传输服务器
     * <p>
     * 创建上传目录（如果不存在），初始化服务器实例并启动。
     * 设置上传回调监听器处理上传成功、失败和服务器启动失败的情况。
     * 更新界面状态显示和按钮文本。
     */
    private void startServer() {
        // 确保上传目录存在
        File uploadDir = AppProfile.DOWNLOADS_DIR;
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 创建并启动服务器
        server = new WifiTransferServer(this, uploadDir, new WifiTransferServer.UploadCallback() {
            @Override
            public void onUploadSuccess(String fileName) {
                runOnUiThread(() -> {
                    Toast.makeText(WifiTransferActivity.this, getString(R.string.upload_success) + " " + fileName, Toast.LENGTH_SHORT).show();
                    scanNewBooks();
                });
            }

            @Override
            public void onUploadError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(WifiTransferActivity.this, getString(R.string.upload_failed) + " " + error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onServerStartError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(WifiTransferActivity.this, getString(R.string.service_start_failed) + " " + error, Toast.LENGTH_SHORT).show();
                    statusText.setText(getString(R.string.service_not_started));
                    startStopButton.setText(getString(R.string.start_service));
                    isServerRunning = false;
                });
            }
        });

        server.start();
        isServerRunning = true;
        // 更新状态显示：服务器运行中、访问地址、保存路径
        statusText.setText(getString(R.string.service_running) + "\n" + getString(R.string.please_access_in_browser) + " http://" + getIPAddress() + ":18080\n" + getString(R.string.books_will_be_saved_to) + " " + uploadDir.getPath());
        startStopButton.setText(getString(R.string.stop_service));
        Toast.makeText(this, getString(R.string.wlan_transfer_service_started), Toast.LENGTH_SHORT).show();
    }

    /**
     * 扫描新上传的书籍
     * <p>
     * 调用后台任务扫描下载目录，更新书库列表。
     */
    private void scanNewBooks() {
        SearchAllBooksWorker.run(getApplicationContext());
    }

    /**
     * 停止WiFi传输服务器
     * <p>
     * 停止服务器，更新界面状态和按钮文本，显示停止提示。
     */
    private void stopServer() {
        if (server != null) {
            server.stop();
        }
        isServerRunning = false;
        statusText.setText(getString(R.string.service_stopped));
        startStopButton.setText(getString(R.string.start_service));
        Toast.makeText(this, getString(R.string.wlan_transfer_service_stopped), Toast.LENGTH_SHORT).show();
    }

    /**
     * 活动销毁时的清理操作
     * <p>
     * 确保服务器在活动销毁时被停止，释放资源。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
    }
}