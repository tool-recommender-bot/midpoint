/*
 * Copyright (c) 2010-2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.notifications.api;

import com.evolveum.midpoint.notifications.api.events.Event;
import com.evolveum.midpoint.notifications.api.events.ModelEvent;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

import java.util.List;

/**
 * Useful functions to be used in notifications scripts (including velocity templates).
 *
 * @author mederly
 */
public interface NotificationFunctions {

	String getShadowName(PrismObject<? extends ShadowType> shadow);

	String getPlaintextPasswordFromDelta(ObjectDelta delta);

	String getContentAsFormattedList(Event event, boolean showSynchronizationItems, boolean showAuxiliaryAttributes);

	List<ItemPath> getSynchronizationPaths();

	List<ItemPath> getAuxiliaryPaths();

	// TODO: polish this method
	// TODO indicate somehow if password was erased from the focus
	// We should (probably) return only a value if it has been (successfully) written to the focus.
	String getFocusPasswordFromEvent(ModelEvent modelEvent);

}
