/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aries.subsystem.core.obr;

import static org.apache.aries.application.utils.AppConstants.LOG_ENTRY;
import static org.apache.aries.application.utils.AppConstants.LOG_EXIT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.aries.subsystem.core.internal.Activator;
import org.apache.aries.subsystem.core.obr.felix.OsgiResourceAdapter;
import org.apache.felix.bundlerepository.Reason;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.osgi.framework.resource.Capability;
import org.osgi.framework.resource.Requirement;
import org.osgi.framework.resource.Resource;
import org.osgi.framework.resource.Wire;
import org.osgi.framework.resource.Wiring;
import org.osgi.service.resolver.Environment;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.Resolver;
import org.osgi.service.subsystem.SubsystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubsystemResolver implements Resolver {
	private static final Logger logger = LoggerFactory.getLogger(SubsystemResolver.class);
	
	private static void addCapabilities(Collection<Capability> capabilities, Environment environment, Requirement requirement) {
		Collection<Capability> caps = environment.findProviders(requirement);
		if (caps.isEmpty())
			return;
		Capability capability = caps.iterator().next();
		if (capabilities.contains(capability))
			return;
		capabilities.add(capability);
		addCapabilities(capabilities, environment, capability.getResource().getRequirements(null));
	}
	
	private static void addCapabilities(Collection<Capability> capabilities, Environment environment, List<Requirement> requirements) {
		for (Requirement requirement : requirements) {
			addCapabilities(capabilities, environment, requirement);
		}
	}

	@Override
	public Map<Resource, List<Wire>> resolve(Environment environment, Collection<? extends Resource> mandatory, Collection<?  extends Resource> optional) throws ResolutionException {
		if (logger.isDebugEnabled())
			logger.debug(LOG_ENTRY, "resolve", new Object[]{environment, mandatory, optional});
		Collection<Capability> capabilities = new ArrayList<Capability>();
		/*
		 * TODO Until an implementation of Resolver comes along, need to find as many resources with capabilities satisfying as
		 * many requirements as possible. This is because the Felix OBR resolver does not make use of this environment. In particular,
		 * we need to add resources that come from subsystem archives or constituents as these will not be available otherwise.
		 */
		List<Resource> resources = new ArrayList<Resource>();
		for (Resource resource : mandatory) {
			resources.add(resource);
			addCapabilities(capabilities, environment, resource.getRequirements(null));
		}
		// TODO Treating optional resources as mandatory for now....
		for (Resource resource : optional) {
			resources.add(resource);
			addCapabilities(capabilities, environment, resource.getRequirements(null));
		}
		for (Capability capability : capabilities) {
			resources.add(capability.getResource());
		}
		org.apache.felix.bundlerepository.Resolver resolver = Activator.getInstance().getServiceProvider().getService(RepositoryAdmin.class).resolver();
        for (Resource resource : resources) {
            resolver.add(new OsgiResourceAdapter(resource));
        }
//        if (resolver.resolve()) {
//        	/* 
//        	 * TODO For now, these need to go back through the environment in order to be sure the URL is available.
//        	 * This is because RepositoryAdmin is not going through the environment as part of pulling in transitive
//        	 * dependencies. Once a "real" Resolver is available, this will no longer be necessary.
//        	 */
//        	for (org.apache.felix.bundlerepository.Resource resource : resolver.getRequiredResources()) {
//        		Resource r = new FelixResourceAdapter(resource);
//        		// Make the environment aware of the resource and its URL.
//        		environment.findProviders(new OsgiIdentityRequirement(r, true));
//            	resources.add(r);
//        	}
//        	for (org.apache.felix.bundlerepository.Resource resource : resolver.getOptionalResources()) {
//        		Resource r = new FelixResourceAdapter(resource);
//        		// Make the environment aware of the resource and its URL.
//        		environment.findProviders(new OsgiIdentityRequirement(r, true));
//            	resources.add(r);
//        	}
//        }
//        else {
        if (!resolver.resolve()) {
            Reason[] reasons = resolver.getUnsatisfiedRequirements();
            StringBuilder builder = new StringBuilder("Failed to resolve subsystem").append(System.getProperty("line.separator"));
            for (Reason reason : reasons)
                builder
                	.append("resource = ")
                	.append(reason.getResource().getSymbolicName())
                	.append(", requirement = ")
                	.append(reason.getRequirement().getName())
                	.append(System.getProperty("line.separator"));
            // TODO Throw ResolutionException instead.
            throw new SubsystemException(builder.toString());
        }
        Map<Resource, List<Wire>> result = new HashMap<Resource, List<Wire>>(resources.size());
		for (Resource resource : resources) {
			result.put(resource, Collections.EMPTY_LIST);
		}
		logger.debug(LOG_EXIT, "resolve", result);
		return result;
	}
}