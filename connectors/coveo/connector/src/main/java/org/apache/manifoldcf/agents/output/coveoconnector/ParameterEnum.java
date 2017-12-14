/*/* $Id: ParameterEnum.java 988245 2017-12-12 16:39:35Z jfcloutier $ */

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.manifoldcf.agents.output.coveoconnector;

import java.util.HashMap;
import java.util.Map;

/** Parameters constants */
public enum ParameterEnum {
    apibaseurl("https://push.cloud.coveo.com/v1"),
    organizationid("MyOrganizationIdGoesHere"),
    sourceid("MySourceIdGoesHere"),
    apikey("xx9999-999-999999-999-99");

    final protected String defaultValue;

    private ParameterEnum(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
