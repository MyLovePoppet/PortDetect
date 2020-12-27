import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PortDetectFrame extends JFrame {
    private JTextField ipInputTextField;
    private JButton button;
    private JTextArea logTextArea;

    private final ExecutorService executorService;
    private static final int threadNum = 5;
    private static final int timeout = 200;

    private final Pattern pattern = Pattern.compile("((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2}\\.){3}\\*:\\d+");
    private Thread thread = null;

    /**
     * 端口探测
     *
     * @param ipAddressPrefix ip前三个
     * @param port            端口号
     * @return 开启的集合
     */
    public java.util.List<String> portDetect(String ipAddressPrefix, int port) {
        CountDownLatch countDownLatch = new CountDownLatch(254);
        java.util.Queue<String> ipAddressQueue = Stream.iterate(1, i -> i + 1)
                .limit(254)
                .map(i -> ipAddressPrefix + i).collect(Collectors.toCollection(LinkedList::new));
        List<String> res = Collections.synchronizedList(new ArrayList<>());
        while (!ipAddressQueue.isEmpty()) {
            String currentIp = ipAddressQueue.poll();
            executorService.submit(() -> {
                String str = Thread.currentThread() + "->" + currentIp;
                try {
                    InetSocketAddress inetSocketAddress = new InetSocketAddress(Inet4Address.getByName(currentIp), port);
                    Socket socket = new Socket();
                    socket.connect(inetSocketAddress, timeout);
                    EventQueue.invokeLater(() -> logTextArea.append(str + "\t连接成功...\n"));
                    res.add(currentIp);
                    socket.close();
                } catch (IOException e) {
                    EventQueue.invokeLater(() -> logTextArea.append(str + "\t连接失败...\n"));
                }
                countDownLatch.countDown();
            });
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException ignored) {
        }
        return res;
    }

    public PortDetectFrame() {
        setLayout(new BorderLayout());

        executorService = Executors.newFixedThreadPool(threadNum);

        ipInputTextField = new JTextField();
        ipInputTextField.setColumns(20);
        button = new JButton("开始探测");
        button.addActionListener(e -> {
            String str = ipInputTextField.getText();
            Matcher matcher = pattern.matcher(str);
            if (!matcher.find()) {
                JOptionPane.showConfirmDialog(this, "请输入正确的信息，" +
                        "例如172.29.13.*:3389表示查找ip地址：172.29.13.*，端口号3389开启的ip地址。", "输入出错", JOptionPane.YES_NO_OPTION);
                return;
            }
            int index = str.indexOf(':');
            String ipPrefix = str.substring(0, index - 1);
            int port = Integer.parseInt(str.substring(index + 1));
            thread = new Thread(() -> {
                logTextArea.setText("");
                List<String> strings = portDetect(ipPrefix, port);
                logTextArea.append("\n\n探测结果，如下ip地址的" + port + "端口是开启的:\n");
                strings.forEach(s -> logTextArea.append(s + "\n"));
            });
            thread.start();
        });
        JPanel panel = new JPanel(new FlowLayout());
        panel.add(ipInputTextField);
        panel.add(button);
        add(panel, BorderLayout.NORTH);


        logTextArea = new JTextArea();
        logTextArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        add(scrollPane, BorderLayout.CENTER);


        setSize(600, 400);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                executorService.shutdownNow();
                if (thread != null) {
                    if (thread.getState() == Thread.State.RUNNABLE
                            || thread.getState() == Thread.State.WAITING
                            || thread.getState() == Thread.State.TIMED_WAITING){
                        thread.interrupt();
                    }
                }
            }
        });
        setVisible(true);
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            JFrame frame = new PortDetectFrame();
        });
    }
}
