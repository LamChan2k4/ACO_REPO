package com.example.acowebservice;

import org.springframework.stereotype.Service;

@Service
public class TaskControlService {
    private Thread currentRunningThread;

    public synchronized void interruptExistingTask() {
        if (currentRunningThread != null && currentRunningThread.isAlive()) {
            System.out.println("⚠️ [SYSTEM] Đang cưỡng chế dừng tiến trình cũ để giải phóng CPU...");
            currentRunningThread.interrupt();
        }
    }

    public synchronized void registerTask(Thread thread) {
        this.currentRunningThread = thread;
    }
}