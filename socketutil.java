package com.gongjiaozaixian.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Date;

/**
 * Created by surpass on 2017/10/23.
 * ע�⣺Sent����һ�����������̵߳��ã���Ϊ���õ���mSocket�������������ǽ�ֹ�����̲߳�����
 * �������� NetworkOnMainThreadException�쳣
 * http://blog.csdn.net/mad1989/article/details/25964495
 */
class SocketStauts {
    //���ӳɹ� ���ͳɹ� ���ճɹ� ����ʧ�� �׽��ִ��� �׽��ַ���ʧ��
    public static int SocketConnectting = 0, SocketConnected = 1, SocketSended = 2, SocketReseived = 3, SocketConnectFaild = -1, SocketErr = -2, SocketSendFaild = -3;
}

public class SocketUtil extends Thread {

    String TAG = "SocketUtil";
    String hostIp = "";//����IP��ַ
    int hostSocketPort = 80;//�����˿�

    Context mContext;

    //�������̣߳�������ʱ���  �ɷ��Ͷ���������
    Thread NetworkCheckThread;
    //�����̣߳�ʵ��������ʱ������  ���ñ����startʱ ͬʱ�������̣߳�
    Thread HeartThread;
    //������δ����supersocket������io�쳣������ʹ�÷����ݹ飬�������ֱ�����ֻ�����߳��ظ�����
    Thread SocketConnectWhenIOErrThread;

    //��������״̬�����ࣨÿ�η��Ͷ�����ã�
    ConnectivityManager connectivityManager;
    NetworkInfo mobNetInfo;
    NetworkInfo wifiNetInfo;

    final int ConnectTimeOut = 3000;
    Date ReceiveDataTime;//���ͷ�������ݵ�ǰʱ������ʱ�����Աȣ��������3�룬���жϷ������Ͽ����ӣ������Ĺؼ�����֮һ
    int HeartTime = ConnectTimeOut;//��������ʱ����  ����ֵ����Ҫ����ConnectTimeOut ����δ�����Ͼͻ��ж� Ϊ������������
    public Socket mSocket = null;//=======�ͻ����׽���
    PrintWriter printWriter;//===========Socket���Ͷ���
    BufferedReader bufferedReader;//=====Socket���ն���
    InputStreamReader mInputStreamReader;
    OutputStreamWriter mOutputStreamWriter;

    String HeartPackage = "0";

    boolean isRun = true;//===============�Ƿ��������� ���ڽ��� socket����  �� �˳�Socket�ı��
    boolean isConnecttingServer = false;//=====�Ƿ��������ӷ�����
    public boolean Connected = false;//=======�ж��Ƿ�ͷ��������� ����

    public Handler mHandler;//=============���̴߳����� �����ô���������


    public SocketUtil(Context context) {
        mContext = context;
    }

    public void CreateHeartThreadAndStart() {
        if (HeartThread == null) {
            //===================================�����߳�
            HeartThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isRun) {
                        try {
                            Thread.sleep(HeartTime);
                            Send(HeartPackage);
                            Log.d(TAG, "[SocketUtil]��������");
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }

                    }
                }
            });
        }
        HeartThread.start();
        //����Ҫ��������1�룬��Ϊ�߳�ִ��ʱ�䲢���м�ʮ�������
        HeartTime = ConnectTimeOut;
        HeartTime = HeartTime + 1000;
    }

    //����socket������
    //1.�����߳�start����ʱ���ô˷���
    //1.���ӳ���IO����ʱ  ���µ��ô˷���
    //1.��ʱ���ͷ������ô˷���  �ĳ������̵߳��ô˷���
    public void ConnectToServer() {

        //���뿪��һ�����߳������ӣ���ֹ���̵߳���
        Runnable r = new Runnable() {
            @Override
            public void run() {
                Connected = false;

                Log.d(TAG, "[SocketUtil]�������ӵ���������Socket���ӣ�");
                if (!NetworkIsConnected()) {
                    CreateAndStartNetWorkConnetFaile();
                    return;
                }

                if (isConnecttingServer) {
                    Log.d(TAG, "[SocketUtil]�������ӷ��������޷��ٴ�������!");
                    return;
                }

                isConnecttingServer = true;
                mSocket = null;


                mSocket = new Socket();
                // synchronized (mSocket) {
                if (isRun) {
                    if (mHandler != null) {
                        Message msg_s = mHandler.obtainMessage();
                        msg_s.what = SocketStauts.SocketConnectting;
                        mHandler.sendMessage(msg_s);
                    }
                }
                SocketAddress socAddress = new InetSocketAddress(hostIp, hostSocketPort);
                try {
                    mSocket.connect(socAddress, ConnectTimeOut);//�������ӳ�ʱʱ��  �˷������������� �������ʧ�� ����Ĵ����޷�ִ��
                    ReceiveDataTime = new Date(System.currentTimeMillis());
                    StopSocketConnectWhenIOErrThread();
                    Connected = true;
                    mSocket.setKeepAlive(true);
                    //mSocket.setSoTimeout(timeout);// ��������ʱ��
                    InputStream is = mSocket.getInputStream();
                    OutputStream os = mSocket.getOutputStream();
                    mInputStreamReader = new InputStreamReader(is);
                    mOutputStreamWriter = new OutputStreamWriter(os);

                    bufferedReader = new BufferedReader(mInputStreamReader);
                    printWriter = new PrintWriter(new BufferedWriter(mOutputStreamWriter), true);

                    //=========���ӳɹ�����֪ͨ�����߳�
                    if (isRun) {
                        if (mHandler != null) {
                            Message msg_e = mHandler.obtainMessage();
                            msg_e.what = SocketStauts.SocketConnected;
                            mHandler.sendMessage(msg_e);
                        }
                        isConnecttingServer = false;
                    }
                }
                //���ӳ�ʱ����ô˶���
                catch (IOException e) {
                    isConnecttingServer = false;

                    if (isRun) {
                        if (mHandler != null) {
                            Message msg_e = mHandler.obtainMessage();
                            msg_e.what = SocketStauts.SocketConnectFaild;
                            msg_e.obj = e.toString();
                            mHandler.sendMessage(msg_e);
                        }
                        isConnecttingServer = false;
                    }

                    Log.d(TAG, "[SocketUtil]" + e);
                    CreateAndStartSocketConnectWhenIOErrThread();
                    return;
                }

            }
        };
        r.run();

    }

    //ʵʱ��������
    @Override
    public void run() {

        CreateHeartThreadAndStart();
        ConnectToServer();


        String line = "";
        while (isRun) {
            try {
                if (mSocket != null && bufferedReader != null) {
                    while ((line = bufferedReader.readLine()) != null && isRun) {
                        Log.d(TAG, "[SocketUtil]������Ϣ����(isRun:" + isRun + ")��" + line);

                        //���½���ʱ��
                        ReceiveDataTime = new Date(System.currentTimeMillis());
                        if (line.equals("1")) {
                            //Log.d(TAG, "�յ�����Ϣ��" + line);

                        }
                        //�����������������ݣ�ΪJSON��ʽ���ַ���
                        else {
                            if (isRun) {
                                //�յ���������������Ϣ
                                if (mHandler != null) {
                                    Message msg = mHandler.obtainMessage();
                                    msg.what = SocketStauts.SocketReseived;
                                    msg.obj = line;
                                    mHandler.sendMessage(msg);// ������ظ�UI����
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    //�ж������Ƿ�����
    public boolean NetworkIsConnected() {

        connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mobNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);


        wifiNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (mobNetInfo.isConnected() || wifiNetInfo.isConnected()) {
            return true;
        } else {
            return false;
        }

    }

    //������ʧ�ܳ���IO����ʱ  �����߳��ظ�����  ֱ�����ӳɹ�
    public void CreateAndStartSocketConnectWhenIOErrThread() {
        if (SocketConnectWhenIOErrThread == null) {
            SocketConnectWhenIOErrThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isRun) {
                        try {
                            Thread.sleep(300);
                            if (!isConnecttingServer) {
                                ConnectToServer();
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
            SocketConnectWhenIOErrThread.start();
        }
    }

    //�����������������߳�
    public void CreateAndStartNetWorkConnetFaile() {
        if (NetworkCheckThread != null) {
            NetworkCheckThread.interrupt();
            NetworkCheckThread = null;
        }

        //�����̣߳�������磬������ͨ��ʱ���ٵ������ӷ���
        NetworkCheckThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRun) {
                    try {
                        Thread.sleep(1000);
                        if (NetworkIsConnected()) {
                            Log.d(TAG, "[SocketUtil]����ͨ�ˣ�����Socket����");
                            ConnectToServer();
                            break;
                        } else {
                            Log.d(TAG, "[SocketUtil]���粻ͨ���޷�����Socket����");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        NetworkCheckThread.start();
    }

    //ֹͣIO�������߳�
    public void StopSocketConnectWhenIOErrThread() {
        if (SocketConnectWhenIOErrThread != null) {
            SocketConnectWhenIOErrThread.interrupt();
            SocketConnectWhenIOErrThread = null;
        }
    }

    //��������,���ⲿ�����
    public String Send(String mess) {
        String res = "";
        if (!isRun) {
            res = "��Ǹ~������ֹͣ���У�����ϵ�ͷ�����������:0";
            return res;
        }
        //=========================δ���ӷ�����  ������Ϣ
        if (!Connected) {
            res = "��Ǹ~�޷���������������ӣ����Ժ����ԣ�������:1";
            if (mHandler != null && !mess.equals(HeartPackage)) {
                Message msg = mHandler.obtainMessage();
                msg.obj = res;
                msg.what = SocketStauts.SocketSendFaild;
                mHandler.sendMessage(msg);// ������ظ�UI����
            }
            return res;
        }

        //=========================��������������  ������Ϣ
        if (isConnecttingServer && !mess.equals(HeartPackage)) {
            res = "��Ǹ~�����ύʧ�ܣ�������������������ӣ����Ժ����ԣ�������:2";
            if (mHandler != null) {
                Message msg = mHandler.obtainMessage();
                msg.obj = res;
                msg.what = SocketStauts.SocketSendFaild;
                mHandler.sendMessage(msg);// ������ظ�UI����
            }
            return res;
        }

        if (!NetworkIsConnected() && !mess.equals(HeartPackage)) {
            res = "��������ʧ�ܣ�����������������ԣ�лл��������:3";
            if (mHandler != null) {
                Message msg = mHandler.obtainMessage();
                msg.obj = res;
                msg.what = SocketStauts.SocketSendFaild;
                mHandler.sendMessage(msg);// ������ظ�UI����
            }
            CreateAndStartNetWorkConnetFaile();
            return res;
        }

        //�������һ�ν������ݵ�ʱ��͵�ǰʱ����������3�룬���ж�Ϊʧ��
        Date now = new Date(System.currentTimeMillis());
        long ediff = now.getTime() - ReceiveDataTime.getTime();
        if (ediff > HeartTime + 1000) {
            res = "���Ѻͷ������Ͽ������ӣ����Ժ����ԣ�";
            if (mHandler != null) {
                Message msg = mHandler.obtainMessage();
                msg.obj = res;
                msg.what = SocketStauts.SocketSendFaild;
                mHandler.sendMessage(msg);// ������ظ�UI����
            }

            ConnectToServer();

            return res;
        }

        try {


            printWriter.println(mess + "\r\n");//���з�����Ҫ�������Ͳ���ȥ
            printWriter.flush();
            if (mHandler != null) {
                Message msg = mHandler.obtainMessage();
                msg.what = SocketStauts.SocketSended;
                msg.obj = "200";
                mHandler.sendMessage(msg);// ������ظ�UI����
            }

            return "200";

        } catch (Exception e) {
            if (mHandler != null) {
                Message msg = mHandler.obtainMessage();
                msg.what = SocketStauts.SocketSendFaild;
                msg.obj = "����ʧ�ܣ�ԭ��" + e.toString();
                mHandler.sendMessage(msg);// ������ظ�UI����
            }
            e.printStackTrace();

            return "����ʧ�ܣ�ԭ��" + e.toString();
        }
    }

    //�ر�����
    public void close() {
        Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.1");
        if (mHandler != null) {
            mHandler.removeCallbacks(this);
        }
        Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.2");
        if (NetworkCheckThread != null) {
            NetworkCheckThread.interrupt();
            NetworkCheckThread = null;
        }
        Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.3");
        if (HeartThread != null) {
            HeartThread.interrupt();
            HeartThread = null;
        }
        Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.4");
        if (SocketConnectWhenIOErrThread != null) {
            SocketConnectWhenIOErrThread.interrupt();
            SocketConnectWhenIOErrThread = null;
        }
        Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.5");
        try {
            isRun = false;
            Log.d(TAG, "[SocketUtil]�ر�socket");
            if (mSocket != null) {

                //bufferedReader.close();
                Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.5.1");
                //printWriter.close();
                Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.5.2");
                mSocket.close();
                Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.5.3");
            }
            Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.6");

        } catch (Exception e) {
            Log.d(TAG, "[SocketUtil]close err");
            e.printStackTrace();
            Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.7");
        }

        Log.d(TAG, "[SocketUtil]ֹͣsocket �߳� 1.8");
        this.interrupt();
    }
}