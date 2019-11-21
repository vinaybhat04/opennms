/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.provision.requisition.command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.core.xml.JaxbUtils;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.provision.persist.LocationAwareRequisitionClient;
import org.opennms.netmgt.provision.persist.requisition.Requisition;

@Command(scope = "opennms-provision", name = "show-import", description = "Display the resulting requisition generated by a given URL")
@Service
public class ShowImport implements Action {

    @Reference
    private EventForwarder eventForwarder;

    @Option(name = "-l", aliases = "--location", description = "Location", required = false, multiValued = false)
    String location;

    @Option(name = "-s", aliases = "--system-id", description = "System ID")
    String systemId;

    @Option(name = "-t", aliases = "--ttl", description = "Time to live", required = false, multiValued = false)
    Long ttlInMs;

    @Option(name = "-x", aliases = "--xml", description = "XML Output", required = false, multiValued = false)
    boolean xmlOutput = false;

    @Option(name = "-i", aliases = "--import", description = "Import Requisition", required = false, multiValued = false)
    boolean importRequisition = false;

    @Argument(index = 0, name = "type", description = "Type", required = true, multiValued = false)
    @Completion(ProviderTypeNameCompleter.class)
    String type;

    @Argument(index = 1, name = "parameters", description = "Provide parameters in key=value form", multiValued = true)
    List<String> parameters;

    @Reference
    private LocationAwareRequisitionClient client;

    @Override
    public Object execute() throws Exception {
        final CompletableFuture<Requisition> future = client.requisition()
                .withRequisitionProviderType(type)
                .withParameters(parse(parameters))
                .withLocation(location)
                .withSystemId(systemId)
                .withTimeToLive(ttlInMs)
                .execute();

        while (true) {
            try {
                try {
                    Requisition requisition = future.get(1, TimeUnit.SECONDS);
                    if(importRequisition) {
                        System.out.println();
                        ImportRequisition.sendImportRequisitionEvent(eventForwarder, type, parameters, null);
                        System.out.println();
                    }
                    if (xmlOutput) {
                        return JaxbUtils.marshal(requisition);
                    } else {
                        return requisition;
                    }
                } catch (InterruptedException e) {
                    System.out.println("\nInterrupted.");
                } catch (ExecutionException e) {
                    System.out.printf("\nRequisition retrieval failed with: %s\n", e);
                    e.printStackTrace();
                }
                break;
            } catch (TimeoutException e) {
                // pass
            }
            System.out.print(".");
            System.out.flush();
        }
        return null;
    }

    private static Map<String, String> parse(List<String> attributeList) {
        final Map<String, String> properties = new HashMap<>();
        if (attributeList != null) {
            for (String keyValue : attributeList) {
                int splitAt = keyValue.indexOf("=");
                if (splitAt <= 0) {
                    throw new IllegalArgumentException("Invalid property " + keyValue);
                } else {
                    String key = keyValue.substring(0, splitAt);
                    String value = keyValue.substring(splitAt + 1, keyValue.length());
                    properties.put(key, value);
                }
            }
        }
        return properties;
    }
}
