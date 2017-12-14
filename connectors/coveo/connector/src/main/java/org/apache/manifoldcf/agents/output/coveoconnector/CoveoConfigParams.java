/* $Id: CoveoConfigParams.java 988245 2017-12-12 16:39:35Z jfcloutier $ */

package org.apache.manifoldcf.agents.output.coveoconnector;

import java.util.HashMap;
import java.util.Map;

public class CoveoConfigParams extends HashMap<ParameterEnum, String> {
    private static final long serialVersionUID = -140931785772220099L; // TODO: Generate a real serialVersionUID

    /**
     * Constructor
     */
    protected CoveoConfigParams(ParameterEnum[] params) {
        super(params.length);
    }

    /**
     * Build the a map based on the params contained in the enum
     *
     * @return the map, with default values
     */
    final public Map<String, String> buildMap() {
        Map<String, String> rval = new HashMap<String, String>();
        for (Map.Entry<ParameterEnum, String> entry : this.entrySet()) {
            rval.put(entry.getKey().name(), entry.getValue());
        }
        return rval;
    }
}