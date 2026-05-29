package com.rapptycoon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "game")
public class GameProperties {

    private Tick tick = new Tick();
    private Players players = new Players();
    private Basestations basestations = new Basestations();
    private Score score = new Score();
    private Events events = new Events();
    private Escalation escalation = new Escalation();
    private Rapp rapp = new Rapp();

    public Tick getTick() {
        return tick;
    }

    public void setTick(Tick tick) {
        this.tick = tick;
    }

    public Players getPlayers() {
        return players;
    }

    public void setPlayers(Players players) {
        this.players = players;
    }

    public Basestations getBasestations() {
        return basestations;
    }

    public void setBasestations(Basestations basestations) {
        this.basestations = basestations;
    }

    public Score getScore() {
        return score;
    }

    public void setScore(Score score) {
        this.score = score;
    }

    public Events getEvents() {
        return events;
    }

    public void setEvents(Events events) {
        this.events = events;
    }

    public Escalation getEscalation() {
        return escalation;
    }

    public void setEscalation(Escalation escalation) {
        this.escalation = escalation;
    }

    public Rapp getRapp() {
        return rapp;
    }

    public void setRapp(Rapp rapp) {
        this.rapp = rapp;
    }

    public static class Tick {
        private long interval = 5000;
        private int total = 60;

        public long getInterval() {
            return interval;
        }

        public void setInterval(long interval) {
            this.interval = interval;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

    public static class Players {
        private int min = 2;
        private int max = 6;

        public int getMin() {
            return min;
        }

        public void setMin(int min) {
            this.min = min;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }
    }

    public static class Basestations {
        private int perPlayer = 3;

        public int getPerPlayer() {
            return perPlayer;
        }

        public void setPerPlayer(int perPlayer) {
            this.perPlayer = perPlayer;
        }
    }

    public static class Score {
        private Weight weight = new Weight();

        public Weight getWeight() {
            return weight;
        }

        public void setWeight(Weight weight) {
            this.weight = weight;
        }

        public static class Weight {
            private double money = 0.30;
            private double satisfaction = 0.35;
            private double stability = 0.35;

            public double getMoney() {
                return money;
            }

            public void setMoney(double money) {
                this.money = money;
            }

            public double getSatisfaction() {
                return satisfaction;
            }

            public void setSatisfaction(double satisfaction) {
                this.satisfaction = satisfaction;
            }

            public double getStability() {
                return stability;
            }

            public void setStability(double stability) {
                this.stability = stability;
            }
        }
    }

    public static class Events {
        private double baseRate = 0.3;
        private double playerMultiplier = 0.2;

        public double getBaseRate() {
            return baseRate;
        }

        public void setBaseRate(double baseRate) {
            this.baseRate = baseRate;
        }

        public double getPlayerMultiplier() {
            return playerMultiplier;
        }

        public void setPlayerMultiplier(double playerMultiplier) {
            this.playerMultiplier = playerMultiplier;
        }
    }

    public static class Escalation {
        private int maxLevel = 3;
        private int autoResolveAfter = 5;

        public int getMaxLevel() {
            return maxLevel;
        }

        public void setMaxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        public int getAutoResolveAfter() {
            return autoResolveAfter;
        }

        public void setAutoResolveAfter(int autoResolveAfter) {
            this.autoResolveAfter = autoResolveAfter;
        }
    }

    public static class Rapp {
        private int deploymentTicks = 1;
        private int resolveTicks = 1;

        public int getDeploymentTicks() {
            return deploymentTicks;
        }

        public void setDeploymentTicks(int deploymentTicks) {
            this.deploymentTicks = deploymentTicks;
        }

        public int getResolveTicks() {
            return resolveTicks;
        }

        public void setResolveTicks(int resolveTicks) {
            this.resolveTicks = resolveTicks;
        }
    }
}
