package com.example.acowebservice;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskControlService {
    // Lưu Thread đang chạy theo Session (Ở đây tạm dùng 1 cái duy nhất cho máy cục bộ)
    private Thread currentRunningThread;

    public synchronized void interruptExistingTask() {
        if (currentRunningThread != null && currentRunningThread.isAlive()) {
            System.out.println("⚠️ [SYSTEM] Phát hiện tiến trình cũ đang chạy. Đang cưỡng chế dừng...");
            currentRunningThread.interrupt(); // Gửi tín hiệu dừng
        }
    }

    public synchronized void registerTask(Thread thread) {
        this.currentRunningThread = thread;
    }
}