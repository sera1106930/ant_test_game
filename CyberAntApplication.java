package com.cyberant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;

// --------------------------------------------------------
// DATA MODELS
// --------------------------------------------------------
class Vec2 {
    public double x, y;
    public Vec2(double x, double y) { this.x = x; this.y = y; }
    
    public static double dist(Vec2 v1, Vec2 v2) { return Math.hypot(v1.x - v2.x, v1.y - v2.y); }
    public static Vec2 add(Vec2 v1, Vec2 v2) { return new Vec2(v1.x + v2.x, v1.y + v2.y); }
    public static Vec2 sub(Vec2 v1, Vec2 v2) { return new Vec2(v1.x - v2.x, v1.y - v2.y); }
    public static Vec2 mult(Vec2 v, double s) { return new Vec2(v.x * s, v.y * s); }
    public static Vec2 normal(Vec2 p1, Vec2 p2) {
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double l = Math.hypot(dx, dy);
        return l == 0 ? new Vec2(0,0) : new Vec2(-dy/l, dx/l);
    }
}

class Room {
    public List<Vec2> points = new ArrayList<>();
    public Vec2 center;
    public String type; // "room" or "tunnel"
    
    // Ray-Casting Algorithm
    public boolean contains(double x, double y) {
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            double xi = points.get(i).x, yi = points.get(i).y;
            double xj = points.get(j).x, yj = points.get(j).y;
            
            boolean intersect = ((yi > y) != (yj > y))
                && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }
}

// --------------------------------------------------------
// MAP ENGINE
// --------------------------------------------------------
@Service
class MapEngine {
    public int width = 3000;
    public int height = 3000;
    public List<Room> rooms = new ArrayList<>();

    public void generateNest() {
        rooms.clear();
        List<Vec2> nodes = new ArrayList<>();

        // 1. Surface Room
        createRoom(width/2.0, 200, 200, 120);
        nodes.add(new Vec2(width/2.0, 200));

        // 2. Random Rooms
        for(int i=0; i<20; i++) {
            double cx = 200 + Math.random() * (width - 400);
            double cy = 400 + Math.random() * (height - 600);
            createRoom(cx, cy, 150 + Math.random()*150, 150 + Math.random()*150);
            nodes.add(new Vec2(cx, cy));
        }

        // 3. MST Logic
        Set<Integer> reached = new HashSet<>();
        reached.add(0);
        Set<Integer> unreached = new HashSet<>();
        for(int i=1; i<nodes.size(); i++) unreached.add(i);

        while(!unreached.isEmpty()) {
            double minDist = Double.MAX_VALUE;
            int bestNode = -1;
            Vec2 p1 = null, p2 = null;

            for(int rId : reached) {
                for(int uId : unreached) {
                    double d = Vec2.dist(nodes.get(rId), nodes.get(uId));
                    if(d < minDist) {
                        minDist = d;
                        bestNode = uId;
                        p1 = nodes.get(rId);
                        p2 = nodes.get(uId);
                    }
                }
            }
            reached.add(bestNode);
            unreached.remove(bestNode);
            createTunnel(p1, p2, 60);
        }

        // 4. Random Loops
        for(int k=0; k<8; k++) {
            int id1 = (int)(Math.random() * nodes.size());
            int id2 = (int)(Math.random() * nodes.size());
            if(id1 != id2 && Vec2.dist(nodes.get(id1), nodes.get(id2)) < 800) 
                createTunnel(nodes.get(id1), nodes.get(id2), 60);
        }
    }

    private void createRoom(double cx, double cy, double w, double h) {
        Room r = new Room();
        r.center = new Vec2(cx, cy);
        r.type = "room";
        int steps = 20; 
        for(int i=0; i<steps; i++) {
            double ang = (i / (double)steps) * Math.PI * 2;
            double noise = 0.7 + Math.random() * 0.6;
            double rx = (w/2) * noise;
            double ry = (h/2) * noise;
            r.points.add(new Vec2(cx + Math.cos(ang)*rx, cy + Math.sin(ang)*ry));
        }
        rooms.add(r);
    }

    private void createTunnel(Vec2 p1, Vec2 p2, double width) {
        Room t = new Room();
        t.type = "tunnel";
        
        List<Vec2> path = new ArrayList<>();
        path.add(p1);
        
        Vec2 dir = Vec2.sub(p2, p1);
        for(int i=1; i<=2; i++) {
            double tVal = i / 3.0;
            Vec2 p = Vec2.add(p1, Vec2.mult(dir, tVal));
            Vec2 perp = new Vec2(-dir.y, dir.x);
            double len = Math.hypot(perp.x, perp.y);
            if(len > 0.001) {
                perp = Vec2.mult(perp, (Math.random()-0.5) * 60 / len);
                p = Vec2.add(p, perp);
            }
            path.add(p);
        }
        path.add(p2);

        List<Vec2> leftSide = new ArrayList<>();
        List<Vec2> rightSide = new ArrayList<>();

        for(int i=0; i<path.size(); i++) {
            Vec2 curr = path.get(i);
            Vec2 next = (i < path.size()-1) ? path.get(i+1) : path.get(i);
            Vec2 prev = (i > 0) ? path.get(i-1) : path.get(i);
            
            Vec2 n1 = Vec2.normal(prev, curr);
            Vec2 n2 = Vec2.normal(curr, next);
            Vec2 avgN = Vec2.mult(Vec2.add(n1, n2), 0.5); 
            double l = Math.hypot(avgN.x, avgN.y);
            if(l < 0.001) avgN = new Vec2(0, 1); else avgN = Vec2.mult(avgN, 1.0/l);

            Vec2 off = Vec2.mult(avgN, width/2);
            leftSide.add(Vec2.add(curr, off));
            rightSide.add(Vec2.sub(curr, off));
        }

        t.points.addAll(leftSide);
        for(int i=rightSide.size()-1; i>=0; i--) t.points.add(rightSide.get(i));
        
        rooms.add(t);
    }

    public boolean isValidPosition(double x, double y) {
        for(Room r : rooms) {
            if(r.contains(x, y)) return true;
        }
        return false;
    }
}

// --------------------------------------------------------
// SIMULATION ENTITIES
// --------------------------------------------------------
class Agent {
    public double x, y, angle;
    public boolean active = true;
    public boolean isRed = false;
}

@Service
class SimulationService {
    private final MapEngine map;
    public List<Agent> agents = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public SimulationService(MapEngine map) {
        this.map = map;
        map.generateNest();
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 0, 16, TimeUnit.MILLISECONDS);
    }

    public void spawn() {
        Agent a = new Agent();
        Room start = map.rooms.get(0);
        a.x = start.center.x; 
        a.y = start.center.y;
        a.angle = Math.random() * Math.PI * 2;
        if(Math.random() < 0.2) a.isRed = true;
        agents.add(a);
    }

    public void reset() {
        agents.clear();
        map.generateNest();
    }

    private void tick() {
        // Move Agents
        for(Agent a : agents) {
            if(!a.active) continue;
            
            // Random Wiggle
            a.angle += (Math.random()-0.5)*0.2;
            
            double nextX = a.x + Math.cos(a.angle) * 3;
            double nextY = a.y + Math.sin(a.angle) * 3;
            
            // Checking Collision
            if(map.isValidPosition(nextX, nextY)) {
                a.x = nextX; 
                a.y = nextY;
            } else {
                // Wall Hit: Random Rotate
                a.angle = Math.random() * Math.PI * 2;
                // Optional: Push back slightly to avoid getting stuck
                a.x -= Math.cos(a.angle) * 1;
                a.y -= Math.sin(a.angle) * 1;
            }
        }
    }
}

// --------------------------------------------------------
// CONTROLLER
// --------------------------------------------------------
@RestController
@CrossOrigin(origins = "*") 
@RequestMapping("/api")
public class AntController {

    private final MapEngine mapEngine;
    private final SimulationService simService;

    public AntController(MapEngine mapEngine, SimulationService simService) {
        this.mapEngine = mapEngine;
        this.simService = simService;
    }

    @GetMapping("/map")
    public Map<String, Object> getMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("width", mapEngine.width);
        data.put("height", mapEngine.height);
        data.put("rooms", mapEngine.rooms);
        return data;
    }

    @GetMapping("/state")
    public Map<String, Object> getState() {
        Map<String, Object> state = new HashMap<>();
        state.put("agents", simService.agents);
        return state;
    }

    @PostMapping("/spawn")
    public void spawn() { simService.spawn(); }

    @PostMapping("/reset")
    public void reset() { simService.reset(); }
}

@SpringBootApplication
public class CyberAntApplication {
    public static void main(String[] args) {
        SpringApplication.run(CyberAntApplication.class, args);
    }
}
