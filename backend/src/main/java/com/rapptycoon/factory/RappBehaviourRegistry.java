package com.rapptycoon.factory;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RappBehaviourRegistry {

    private final Map<String, RappBehaviour> behaviours;

    public RappBehaviourRegistry() {
        behaviours = new HashMap<>();
        register(new EnergySaverBehaviour());
        register(new CapacityOptimiserBehaviour());
        register(new FaultPredictorBehaviour());
        register(new SlaGuardianBehaviour());
        register(new ConfigDriftDetectorBehaviour());
        register(new TrafficBalancerBehaviour());
        register(new AlarmNoiseReducerBehaviour());
    }

    private void register(RappBehaviour behaviour) {
        behaviours.put(behaviour.getRappName(), behaviour);
    }

    public RappBehaviour getBehaviour(String rappName) {
        RappBehaviour behaviour = behaviours.get(rappName);
        if (behaviour == null) {
            throw new EntityNotFoundException("No behaviour found for rApp: " + rappName);
        }
        return behaviour;
    }

    public Map<String, RappBehaviour> getAllBehaviours() {
        return Map.copyOf(behaviours);
    }
}
