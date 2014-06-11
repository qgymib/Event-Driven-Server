package com.qgymib.findthetoiletserver;

import com.qgymib.findthetoiletserver.service.NetService;


public class App {

    public static void main(String[] args) {
        NetService service = new NetService();
        Thread deamo = new Thread(service);
        deamo.start();
        try {
            deamo.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
