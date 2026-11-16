/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.elevatordemo;

/**
 *
 * @author Ndivhuwo
 */
import java.awt.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;

public class ElevatorDemo extends JFrame {

    // ----- settings you can change -----
    static final int FLOORS = 10;
    static final int ELEVATORS = 3;
    static final int CAPACITY = 4;      // max people in one elevator

    // A person who wants to travel
    static class Passenger {
        int from;
        int to;
        int elevator;   // number of the elevator that is coming for this person

        Passenger(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }

    // One elevator
    static class Elevator {
        int number;
        int floor = 0;
        int direction = 0;                       // 1 = going up, -1 = going down, 0 = idle
        boolean doorOpen = false;
        boolean[] stops = new boolean[FLOORS];   // true = must stop on that floor
        ArrayList<Passenger> riders = new ArrayList<>();

        Elevator(int number) {
            this.number = number;
        }
    }

    // ----- the building -----
    Elevator[] elevators = new Elevator[ELEVATORS];
    ArrayList<Passenger> waiting = new ArrayList<>();
    int delivered = 0;
    int totalPassengers = 0;
    int tickCount = 0;
    Random random = new Random();

    // ----- screen parts -----
    JPanel buildingPanel;
    JTextArea logArea = new JTextArea();
    JLabel statsLabel = new JLabel();
    JComboBox<Integer> fromBox = new JComboBox<>();
    JComboBox<Integer> toBox = new JComboBox<>();
    JCheckBox autoBox = new JCheckBox("Auto passengers", true);
    JCheckBox rushBox = new JCheckBox("Rush hour (everyone starts on floor 0)");
    Timer timer;

    public ElevatorDemo() {
        super("Elevator Demo");

        // create the elevators
        for (int i = 0; i < ELEVATORS; i++) {
            elevators[i] = new Elevator(i + 1);
        }

        // the drawing area
        buildingPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawBuilding(g);
            }
        };
        buildingPanel.setPreferredSize(new Dimension(490, 590));
        buildingPanel.setBackground(Color.WHITE);

        // the log on the right
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(280, 100));

        // the buttons at the bottom
        for (int f = 0; f < FLOORS; f++) {
            fromBox.addItem(f);
            toBox.addItem(f);
        }
        toBox.setSelectedIndex(FLOORS - 1);

        JButton callButton = new JButton("Call elevator");
        callButton.addActionListener(e -> callElevator());

        JPanel controls = new JPanel();
        controls.add(new JLabel("From floor"));
        controls.add(fromBox);
        controls.add(new JLabel("To floor"));
        controls.add(toBox);
        controls.add(callButton);
        controls.add(autoBox);
        controls.add(rushBox);

        // put everything in the window
        setLayout(new BorderLayout());
        add(statsLabel, BorderLayout.NORTH);
        add(buildingPanel, BorderLayout.CENTER);
        add(logScroll, BorderLayout.EAST);
        add(controls, BorderLayout.SOUTH);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);

        // every 700 ms the whole building moves one step
        timer = new Timer(700, e -> tick());
        timer.start();
        updateStats();
    }

    // ----- one step of the simulation -----
    void tick() {
        tickCount++;
        makeRandomPassenger();

        for (int i = 0; i < ELEVATORS; i++) {
            moveElevator(elevators[i]);
        }

        updateStats();
        buildingPanel.repaint();
    }

    void makeRandomPassenger() {
        if (!autoBox.isSelected()) {
            return;
        }

        // more people appear during rush hour
        int chance = 35;
        if (rushBox.isSelected()) {
            chance = 80;
        }
        if (random.nextInt(100) >= chance) {
            return;
        }

        int from;
        int to;
        if (rushBox.isSelected()) {
            from = 0;                                // everyone starts in the lobby
            to = 1 + random.nextInt(FLOORS - 1);     // and goes up
        } else {
            from = random.nextInt(FLOORS);
            to = random.nextInt(FLOORS);
            while (to == from) {
                to = random.nextInt(FLOORS);
            }
        }
        addPassenger(from, to);
    }

    // button click
    void callElevator() {
        int from = (Integer) fromBox.getSelectedItem();
        int to = (Integer) toBox.getSelectedItem();
        if (from == to) {
            log("Please pick two different floors.");
            return;
        }
        addPassenger(from, to);
        buildingPanel.repaint();
    }

    void addPassenger(int from, int to) {
        Passenger p = new Passenger(from, to);
        p.elevator = pickElevator(from, -1);
        elevators[p.elevator].stops[from] = true;   // that elevator must stop here
        waiting.add(p);
        totalPassengers++;
        log("Person wants floor " + from + " -> " + to + "  (E" + (p.elevator + 1) + " is coming)");
        updateStats();
    }

    // Choose the best elevator for a floor. "skip" is an elevator we don't want (-1 = none).
    int pickElevator(int floor, int skip) {
        int best = 0;
        int bestScore = 99999;

        for (int i = 0; i < ELEVATORS; i++) {
            if (i == skip) {
                continue;
            }
            Elevator e = elevators[i];

            int score = Math.abs(e.floor - floor);   // closer is better
            for (int f = 0; f < FLOORS; f++) {
                if (e.stops[f]) {
                    score = score + 2;               // a busy elevator is worse
                }
            }
            if (e.riders.size() >= CAPACITY) {
                score = score + 20;                  // a full elevator is much worse
            }

            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    // What one elevator does in one step
    void moveElevator(Elevator e) {
        e.doorOpen = false;

        // 1. Is this floor a stop? Then open the door and stay here for this step.
        if (e.stops[e.floor]) {
            e.stops[e.floor] = false;
            e.doorOpen = true;

            // people get out
            for (int i = e.riders.size() - 1; i >= 0; i--) {
                Passenger p = e.riders.get(i);
                if (p.to == e.floor) {
                    e.riders.remove(i);
                    delivered++;
                    log("E" + e.number + " dropped someone off at floor " + e.floor);
                }
            }

            // people get in
            for (int i = waiting.size() - 1; i >= 0; i--) {
                Passenger p = waiting.get(i);
                if (p.from == e.floor && p.elevator == e.number - 1) {
                    if (e.riders.size() < CAPACITY) {
                        e.riders.add(p);
                        e.stops[p.to] = true;        // now it must go to their floor
                        waiting.remove(i);
                        log("E" + e.number + " picked someone up at floor " + e.floor);
                    } else {
                        // elevator is full, send another elevator
                        p.elevator = pickElevator(p.from, e.number - 1);
                        elevators[p.elevator].stops[p.from] = true;
                        log("E" + e.number + " is FULL, E" + (p.elevator + 1) + " will come instead");
                    }
                }
            }
            return;
        }

        // 2. Are there stops above or below us?
        boolean stopAbove = false;
        boolean stopBelow = false;
        for (int f = e.floor + 1; f < FLOORS; f++) {
            if (e.stops[f]) {
                stopAbove = true;
            }
        }
        for (int f = 0; f < e.floor; f++) {
            if (e.stops[f]) {
                stopBelow = true;
            }
        }

        // nothing to do
        if (!stopAbove && !stopBelow) {
            e.direction = 0;
            return;
        }

        // 3. Choose a direction: keep going the same way until there is nothing left, then turn around
        if (e.direction == 1 && !stopAbove) {
            e.direction = -1;
        } else if (e.direction == -1 && !stopBelow) {
            e.direction = 1;
        } else if (e.direction == 0) {
            if (stopAbove) {
                e.direction = 1;
            } else {
                e.direction = -1;
            }
        }

        // 4. Move one floor
        e.floor = e.floor + e.direction;
    }

    int countWaiting(int floor) {
        int count = 0;
        for (Passenger p : waiting) {
            if (p.from == floor) {
                count++;
            }
        }
        return count;
    }

    void log(String message) {
        logArea.append("[" + tickCount + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    void updateStats() {
        statsLabel.setText("  Step " + tickCount + "   |   Delivered " + delivered + " / " + totalPassengers
                + "   |   Capacity " + CAPACITY + " per elevator");
    }

    // ----- drawing -----
    void drawBuilding(Graphics g) {
        int floorHeight = 55;
        int top = 20;
        int left = 150;
        int shaftWidth = 90;
        int gap = 15;
        int rightEdge = left + ELEVATORS * (shaftWidth + gap);

        // floors, with how many people wait on each
        for (int f = 0; f < FLOORS; f++) {
            int y = top + (FLOORS - 1 - f) * floorHeight;
            g.setColor(Color.LIGHT_GRAY);
            g.drawLine(10, y + floorHeight, rightEdge, y + floorHeight);
            g.setColor(Color.BLACK);
            g.drawString("Floor " + f, 15, y + 25);
            g.drawString("waiting: " + countWaiting(f), 15, y + 42);
        }

        // elevators
        for (int i = 0; i < ELEVATORS; i++) {
            Elevator e = elevators[i];
            int x = left + i * (shaftWidth + gap);

            // the shaft
            g.setColor(new Color(235, 235, 235));
            g.fillRect(x, top, shaftWidth, FLOORS * floorHeight);

            // red dots = floors this elevator will stop at
            g.setColor(Color.RED);
            for (int f = 0; f < FLOORS; f++) {
                if (e.stops[f]) {
                    int dotY = top + (FLOORS - 1 - f) * floorHeight + 6;
                    g.fillOval(x + shaftWidth - 14, dotY, 8, 8);
                }
            }

            // the elevator itself
            int y = top + (FLOORS - 1 - e.floor) * floorHeight;
            if (e.riders.size() >= CAPACITY) {
                g.setColor(new Color(198, 40, 40));       // red = full
            } else if (e.direction == 1) {
                g.setColor(new Color(76, 175, 80));       // green = going up
            } else if (e.direction == -1) {
                g.setColor(new Color(255, 152, 0));       // orange = going down
            } else {
                g.setColor(Color.GRAY);                   // gray = idle
            }
            g.fillRect(x + 5, y + 3, shaftWidth - 25, floorHeight - 6);

            // the door: black = open, light gray = closed
            if (e.doorOpen) {
                g.setColor(Color.BLACK);
            } else {
                g.setColor(Color.LIGHT_GRAY);
            }
            g.fillRect(x + 15, y + 26, shaftWidth - 45, 22);

            // label: name, direction and number of people
            char arrow = '-';
            if (e.direction == 1) {
                arrow = '^';
            } else if (e.direction == -1) {
                arrow = 'v';
            }
            g.setColor(Color.WHITE);
            g.drawString("E" + e.number + " " + arrow + " " + e.riders.size() + "/" + CAPACITY, x + 10, y + 20);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ElevatorDemo demo = new ElevatorDemo();
            demo.setVisible(true);
        });
    }
}