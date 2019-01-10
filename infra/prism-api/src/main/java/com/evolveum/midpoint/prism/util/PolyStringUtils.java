/**
 * Copyright (c) 2018 Evolveum
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
package com.evolveum.midpoint.prism.util;

import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.prism.xml.ns._public.types_4.PolyStringType;

/**
 * @author semancik
 *
 */
public class PolyStringUtils {

	public static boolean isEmpty(PolyString polyString) {
		if (polyString == null) {
			return true;
		}
		return polyString.isEmpty();
	}
	
	public static boolean isNotEmpty(PolyString polyString) {
		if (polyString == null) {
			return false;
		}
		return !polyString.isEmpty();
	}
	
	public static boolean isEmpty(PolyStringType polyString) {
		if (polyString == null) {
			return true;
		}
		return polyString.isEmpty();
	}
	
	public static boolean isNotEmpty(PolyStringType polyString) {
		if (polyString == null) {
			return false;
		}
		return !polyString.isEmpty();
	}
	
}