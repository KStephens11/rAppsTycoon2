package com.rapptycoon.factory;

import com.rapptycoon.model.Aggressiveness;
import com.rapptycoon.model.MetricDeltas;

public interface RappBehaviour {

    MetricDeltas calculateImpact(Aggressiveness aggressiveness);

    String getRappName();
}
