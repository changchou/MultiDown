package com.zhang.multidown;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private EditText etUrl;
    private Button btnDown;
    private int total = 0;
    private boolean downloading = false;
    private URL url;
    private File file;
    private List<HashMap<String, Integer>> threadList;
    private int length;

//    Handler handler = new Handler(new Handler.Callback() {
//        @Override
//        public boolean handleMessage(Message msg) {
//            if (msg.what == 0) {
//                progressBar.setProgress(msg.arg1);
//                if (msg.arg1 == length) {
//                    Toast.makeText(MainActivity.this, "下载完成！", Toast.LENGTH_SHORT).show();
//                    total = 0;
//                }
//            }
//            return false;
//        }
//    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        etUrl = (EditText) findViewById(R.id.et_fileUrl);
        btnDown = (Button) findViewById(R.id.btn_down);
        threadList = new ArrayList<>();

    }

    public void onClickDown(View view) {

        if (downloading) {
            downloading = false;
            btnDown.setText("下载");
            return;
        }
        downloading = true;
        btnDown.setText("暂停");

        if (threadList.size() == 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        url = new URL(etUrl.getText().toString());
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(5000);
                        connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.1; Trident/4.0; .NET CLR 2.0.50727)");
                        length = connection.getContentLength();
                        progressBar.setMax(length);
                        progressBar.setProgress(0);

                        if (length < 0) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "File not found !", Toast.LENGTH_SHORT).show();
                                }
                            });

                            return;
                        }

                        file = new File(Environment.getExternalStorageDirectory(), getFileName(etUrl.getText().toString()));
                        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
                        randomAccessFile.setLength(length);

                        int blockSize = length / 3;
                        for (int i = 0; i < 3; i++) {
                            int begin = i * blockSize;
                            int end = (i + 1) * blockSize - 1;
                            if (i == 2) {
                                end = length;
                            }

                            HashMap<String, Integer> map = new HashMap<>();
                            map.put("begin", begin);
                            map.put("end", end);
                            map.put("finished", 0);
                            threadList.add(map);

                            //创建线程 下载文件
                            new Thread(new DownloadRunnable(begin, end, i, file, url)).start();
                        }

                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "URL Error !", Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } else {
            //恢复下载
            for (int i = 0; i < threadList.size(); i++) {
                HashMap<String, Integer> map = threadList.get(i);
                int begin = map.get("begin");
                int end = map.get("end");
                int finished = map.get("finished");
                new Thread(new DownloadRunnable(begin + finished, end, i, file, url)).start();
            }
        }
    }

    private String getFileName(String url) {
        int index = url.lastIndexOf("/") + 1;
        return url.substring(index);
    }

    class DownloadRunnable implements Runnable {

        private int begin;
        private int end;
        private int id;
        private File file;
        private URL url;

        public DownloadRunnable(int begin, int end, int id, File file, URL url) {
            this.begin = begin;
            this.end = end;
            this.id = id;
            this.file = file;
            this.url = url;
        }

        @Override
        public void run() {
            try {
                if (begin > end) {
                    return;
                }
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.1; Trident/4.0; .NET CLR 2.0.50727)");
                connection.setRequestProperty("Range", "bytes=" + begin + "-" + end);

                InputStream is = connection.getInputStream();
                byte[] buf = new byte[1024 * 1024];
                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
                randomAccessFile.seek(begin);
                int len;
                HashMap<String, Integer> map = threadList.get(id);
                while ((len = is.read(buf)) != -1 && downloading) {
                    randomAccessFile.write(buf, 0, len);
                    updateProgress(len);
                    map.put("finished", map.get("finished") + len);
                    System.out.println("Download:" + total);
                }
                is.close();
                randomAccessFile.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    synchronized private void updateProgress(int len) {
        total += len;
//        handler.obtainMessage(0, total, 0).sendToTarget();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setProgress(total);
                if (total == length) {
                    Toast.makeText(MainActivity.this, "下载完成！", Toast.LENGTH_SHORT).show();
                    total = 0;
                    btnDown.setText("完成");
                }
            }
        });
    }
}
