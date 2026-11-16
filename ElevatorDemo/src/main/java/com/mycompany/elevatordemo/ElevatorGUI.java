/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.elevatordemo;

/**
 *
 * @author Ndivhuwo
 */
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

public class ElevatorGUI extends JFrame {

    static final int FLOORS = 10;
    static final int ELEVATORS = 3;
    static final int CAPACITY = 4;   // max riders per car

    enum Direction { UP, DOWN, IDLE }

    // ---------------------------------------------------------------- model

    static class Passenger {
        final int id, from, to, spawnTick;
        Elevator assigned;
        Elevator bumpedBy;      // car that was full when this passenger tried to board
        int boardTick = -1, arriveTick = -1;

        Passenger(int id, int from, int to, int spawnTick) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.spawnTick = spawnTick;
        }
    }

    static class Elevator {
        final int id;
        int floor = 0;              // logical floor (updated every tick)
        double drawPos = 0;         // animated position used for drawing
        boolean doorsOpen = false;  // target door state (set by the simulation)
        double doorOpen = 0;        // animated door opening, 0 = closed, 1 = open
        Direction dir = Direction.IDLE;
        final TreeSet<Integer> stops = new TreeSet<>();
        final List<Passenger> riders = new ArrayList<>();

        Elevator(int id) { this.id = id; }

        boolean isFull() { return riders.size() >= CAPACITY; }

        // Dispatcher cost: lower is better.
        int cost(int target) {
            int c = Math.abs(floor - target) + stops.size() * 2;
            if ((dir == Direction.UP && target < floor) || (dir == Direction.DOWN && target > floor)) {
                c += 10; // already heading away from the caller
            }
            if (isFull()) c += 30;
            return c;
        }

        void step(int tick, Map<Integer, List<Passenger>> waiting, List<Passenger> delivered,
                  List<Passenger> bumped, List<String> log) {
            doorsOpen = false;

            // Serve this floor: doors open, passengers exit, then board. The car dwells one tick.
            if (stops.remove(floor)) {
                doorsOpen = true;

                Iterator<Passenger> it = riders.iterator();
                while (it.hasNext()) {
                    Passenger p = it.next();
                    if (p.to == floor) {
                        p.arriveTick = tick;
                        delivered.add(p);
                        it.remove();
                        log.add("E" + id + " drops off P" + p.id + " at floor " + floor);
                    }
                }

                Iterator<Passenger> wi = waiting.getOrDefault(floor, new ArrayList<>()).iterator();
                while (wi.hasNext()) {
                    Passenger p = wi.next();
                    if (p.assigned != this) continue;
                    if (isFull()) {
                        p.assigned = null;
                        p.bumpedBy = this;
                        bumped.add(p);
                        log.add("E" + id + " is FULL - P" + p.id + " has to wait for another car");
                        continue;
                    }
                    p.boardTick = tick;
                    riders.add(p);
                    stops.add(p.to);
                    wi.remove();
                    log.add("E" + id + " picks up P" + p.id + " at floor " + floor + " -> " + p.to);
                }
                return;
            }

            if (stops.isEmpty()) {
                dir = Direction.IDLE;
                return;
            }

            // SCAN: keep going while there are stops ahead, otherwise reverse
            if (dir == Direction.IDLE) {
                dir = stops.higher(floor) != null ? Direction.UP : Direction.DOWN;
            } else if (dir == Direction.UP && stops.higher(floor) == null) {
                dir = Direction.DOWN;
            } else if (dir == Direction.DOWN && stops.lower(floor) == null) {
                dir = Direction.UP;
            }
            floor += (dir == Direction.UP) ? 1 : -1;
        }
    }

    // Pick the cheapest car for a passenger (a car that just bumped them is skipped).
    static Elevator chooseElevator(List<Elevator> elevators, Passenger p) {
        Elevator best = null;
        int bestCost = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            if (e == p.bumpedBy && elevators.size() > 1) continue;
            int c = e.cost(p.from);
            if (c < bestCost) {
                bestCost = c;
                best = e;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- state

    private final List<Elevator> elevators = new ArrayList<>();
    private final Map<Integer, List<Passenger>> waiting = new HashMap<>();
    private final List<Passenger> all = new ArrayList<>();
    private final List<Passenger> delivered = new ArrayList<>();
    private final Random rnd = new Random();
    private int tick = 0;
    private int nextId = 1;

    private final BuildingPanel panel;
    private final JTextArea logArea = new JTextArea();
    private final JLabel statsLabel = new JLabel();
    private final JComboBox<Integer> fromBox = new JComboBox<>();
    private final JComboBox<Integer> toBox = new JComboBox<>();
    private final JComboBox<String> modeBox =
            new JComboBox<>(new String[]{"Normal", "Morning rush", "Evening rush"});
    private final JCheckBox autoBox = new JCheckBox("Auto passengers", true);
    private final JSlider speedSlider = new JSlider(1, 10, 5);
    private Timer simTimer;
    private Timer animTimer;

    // ---------------------------------------------------------------- UI

    public ElevatorGUI() {
        super("Elevator Building Demo");
        for (int i = 1; i <= ELEVATORS; i++) elevators.add(new Elevator(i));

        panel = new BuildingPanel();

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(300, 100));

        statsLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        for (int f = 0; f < FLOORS; f++) {
            fromBox.addItem(f);
            toBox.addItem(f);
        }
        toBox.setSelectedIndex(FLOORS - 1);

        JButton callBtn = new JButton("Call elevator");
        callBtn.addActionListener(e -> {
            int from = (Integer) fromBox.getSelectedItem();
            int to = (Integer) toBox.getSelectedItem();
            if (from == to) {
                appendLog("Pick two different floors.");
                return;
            }
            addPassenger(from, to);
        });

        JButton pauseBtn = new JButton("Pause");
        pauseBtn.addActionListener(e -> {
            if (simTimer.isRunning()) {
                simTimer.stop();
                pauseBtn.setText("Resume");
            } else {
                simTimer.start();
                pauseBtn.setText("Pause");
            }
        });

        speedSlider.addChangeListener(e -> simTimer.setDelay(delayFor(speedSlider.getValue())));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        controls.add(new JLabel("From"));
        controls.add(fromBox);
        controls.add(new JLabel("To"));
        controls.add(toBox);
        controls.add(callBtn);
        controls.add(autoBox);
        controls.add(new JLabel("Mode"));
        controls.add(modeBox);
        controls.add(new JLabel("Speed"));
        controls.add(speedSlider);
        controls.add(pauseBtn);

        setLayout(new BorderLayout());
        add(statsLabel, BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        add(logScroll, BorderLayout.EAST);
        add(controls, BorderLayout.SOUTH);

        simTimer = new Timer(delayFor(speedSlider.getValue()), e -> tick());
        animTimer = new Timer(16, e -> animate());

        updateStats();
        simTimer.start();
        animTimer.start();
    }

    private static int delayFor(int speed) {
        return 1600 - speed * 150; // speed 1 = slow (1450 ms), speed 10 = fast (100 ms)
    }

    // ---------------------------------------------------------------- simulation

    private void addPassenger(int from, int to) {
        Passenger p = new Passenger(nextId++, from, to, tick);
        all.add(p);
        waiting.computeIfAbsent(from, k -> new ArrayList<>()).add(p);
        dispatch(p);
        appendLog("P" + p.id + " calls at floor " + from + " (to " + to + ") -> E" + p.assigned.id);
        updateStats();
    }

    private void dispatch(Passenger p) {
        Elevator best = chooseElevator(elevators, p);
        p.assigned = best;
        best.stops.add(p.from);
    }

    private int otherFloor(int f) {
        int o;
        do { o = rnd.nextInt(FLOORS); } while (o == f);
        return o;
    }

    // Random passengers; rush modes send most people to or from the lobby (floor 0).
    private void spawnRandom() {
        if (!autoBox.isSelected()) return;
        String mode = (String) modeBox.getSelectedItem();
        double rate = "Normal".equals(mode) ? 0.35 : 0.8;
        if (rnd.nextDouble() >= rate) return;

        int from, to;
        if ("Morning rush".equals(mode)) {
            from = rnd.nextDouble() < 0.85 ? 0 : rnd.nextInt(FLOORS);
            to = otherFloor(from);
        } else if ("Evening rush".equals(mode)) {
            to = rnd.nextDouble() < 0.85 ? 0 : rnd.nextInt(FLOORS);
            from = otherFloor(to);
        } else {
            from = rnd.nextInt(FLOORS);
            to = otherFloor(from);
        }
        addPassenger(from, to);
    }

    private void tick() {
        spawnRandom();

        List<String> log = new ArrayList<>();
        List<Passenger> bumped = new ArrayList<>();
        for (Elevator e : elevators) e.step(tick, waiting, delivered, bumped, log);
        for (String line : log) appendLog(line);

        for (Passenger p : bumped) {
            dispatch(p);
            appendLog("P" + p.id + " re-dispatched to E" + p.assigned.id);
        }

        tick++;
        updateStats();
    }

    // Slide cars toward their logical floor and animate the doors.
    private void animate() {
        int delay = simTimer.getDelay();
        double moveStep = 16.0 / delay;
        double doorStep = 16.0 / (delay * 0.5);
        for (Elevator e : elevators) {
            double diff = e.floor - e.drawPos;
            if (Math.abs(diff) <= moveStep) e.drawPos = e.floor;
            else e.drawPos += Math.signum(diff) * moveStep;

            double target = e.doorsOpen ? 1 : 0;
            double dd = target - e.doorOpen;
            if (Math.abs(dd) <= doorStep) e.doorOpen = target;
            else e.doorOpen += Math.signum(dd) * doorStep;
        }
        panel.repaint();
    }

    private void appendLog(String msg) {
        logArea.append("[" + tick + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void updateStats() {
        double avgWait = delivered.stream().mapToInt(p -> p.boardTick - p.spawnTick).average().orElse(0);
        statsLabel.setText(String.format(
                "Tick %d   |   Delivered %d / %d   |   Avg wait %.1f ticks   |   Capacity %d per car",
                tick, delivered.size(), all.size(), avgWait, CAPACITY));
    }

    // ---------------------------------------------------------------- drawing

    class BuildingPanel extends JPanel {
        BuildingPanel() {
            setPreferredSize(new Dimension(570, 620));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int margin = 20;
            double fh = (getHeight() - 2.0 * margin) / FLOORS;
            int shaftX0 = 190, shaftW = 100, gap = 15;

            // Floors, labels and waiting passengers
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            for (int f = 0; f < FLOORS; f++) {
                int top = (int) (margin + (FLOORS - 1 - f) * fh);
                int mid = top + (int) (fh / 2);
                g.setColor(new Color(220, 220, 220));
                g.drawLine(10, top + (int) fh, getWidth() - 10, top + (int) fh);
                g.setColor(Color.DARK_GRAY);
                g.drawString("Floor " + f, 15, mid + 4);

                int n = waiting.getOrDefault(f, Collections.emptyList()).size();
                g.setColor(new Color(70, 130, 180));
                for (int i = 0; i < Math.min(n, 6); i++) g.fillOval(75 + i * 14, mid - 5, 10, 10);
                if (n > 6) {
                    g.setColor(Color.DARK_GRAY);
                    g.drawString("+" + (n - 6), 75 + 6 * 14, mid + 4);
                }
            }

            // Shafts and cars
            for (int i = 0; i < elevators.size(); i++) {
                Elevator e = elevators.get(i);
                int x = shaftX0 + i * (shaftW + gap);

                g.setColor(new Color(242, 242, 242));
                g.fillRect(x, margin, shaftW, (int) (fh * FLOORS));
                g.setColor(Color.GRAY);
                g.drawRect(x, margin, shaftW, (int) (fh * FLOORS));

                // Pending stops
                g.setColor(new Color(200, 60, 60));
                for (int s : e.stops) {
                    int sy = (int) (margin + (FLOORS - 1 - s) * fh + fh / 2);
                    g.fillOval(x + shaftW - 12, sy - 4, 8, 8);
                }

                // Car body (red when full)
                int cy = (int) (margin + (FLOORS - 1 - e.drawPos) * fh);
                int carX = x + 6, carY = cy + 4, carW = shaftW - 28, carH = (int) fh - 8;
                Color body = e.isFull() ? new Color(198, 40, 40)
                        : e.dir == Direction.UP ? new Color(76, 175, 80)
                        : e.dir == Direction.DOWN ? new Color(255, 152, 0)
                        : new Color(120, 144, 156);
                g.setColor(body);
                g.fill(new RoundRectangle2D.Double(carX, carY, carW, carH, 10, 10));

                // Label and direction arrow
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 11));
                g.drawString("E" + e.id + " " + e.riders.size() + "/" + CAPACITY, carX + 6, carY + 14);
                int ax = carX + carW - 12, ay = carY + 9;
                if (e.dir == Direction.UP) {
                    g.fillPolygon(new int[]{ax - 5, ax + 5, ax}, new int[]{ay + 4, ay + 4, ay - 5}, 3);
                } else if (e.dir == Direction.DOWN) {
                    g.fillPolygon(new int[]{ax - 5, ax + 5, ax}, new int[]{ay - 4, ay - 4, ay + 5}, 3);
                }

                // Interior with riders, covered by two sliding door panels
                int dx = carX + 6, dy = carY + 20, dw = carW - 12, dh = Math.max(carH - 26, 8);
                g.setColor(new Color(35, 35, 35));
                g.fillRect(dx, dy, dw, dh);
                g.setColor(new Color(255, 224, 130));
                for (int r = 0; r < e.riders.size(); r++) {
                    g.fillOval(dx + 4 + r * (dw - 8) / CAPACITY, dy + dh - 14, 10, 10);
                }
                int pw = (int) Math.round(dw / 2.0 * (1 - e.doorOpen));
                g.setColor(new Color(210, 210, 210));
                g.fillRect(dx, dy, pw, dh);
                g.fillRect(dx + dw - pw, dy, pw, dh);
            }
        }
    }

    // ---------------------------------------------------------------- main

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ElevatorGUI gui = new ElevatorGUI();
            gui.setDefaultCloseOperation(EXIT_ON_CLOSE);
            gui.pack();
            gui.setLocationRelativeTo(null);
            gui.setVisible(true);
        });
    }
}