//
// MessagePack for Java
//
// Copyright (C) 2009 - 2013 FURUHASHI Sadayuki
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//        http://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//
package org.msgpack.template;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.msgpack.unpacker.Unpacker;

public class SetUnpackerTemplate<E> extends AbstractUnpackerTemplate<Set<E>> {
	private UnpackerTemplate<E> elementUnpackerTemplate;

	public SetUnpackerTemplate(UnpackerTemplate<E> elementUnpackerTemplate) {
		this.elementUnpackerTemplate = elementUnpackerTemplate;
	}

	public Set<E> read(Unpacker u, Set<E> to, boolean required)
			throws IOException {
		if (!required && u.trySkipNil()) {
			return null;
		}
		int n = u.readArrayHeader();
		if (to == null) {
			to = new HashSet<E>(n);
		} else {
			to.clear();
		}
		for (int i = 0; i < n; i++) {
			E e = elementUnpackerTemplate.read(u, null);
			to.add(e);
		}
		return to;
	}
}
