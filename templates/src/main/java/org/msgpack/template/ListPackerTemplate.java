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
import java.util.List;
import org.msgpack.packer.Packer;
import org.msgpack.MessageTypeException;

public class ListPackerTemplate<E> extends AbstractPackerTemplate<List<E>> {
	private PackerTemplate<E> elementPackerTemplate;

	public ListPackerTemplate(PackerTemplate<E> elementPackerTemplate) {
		this.elementPackerTemplate = elementPackerTemplate;
	}

	public void write(Packer packer, List<E> target, boolean required)
			throws IOException {
		if (!(target instanceof List)) {
			if (target == null) {
				if (required) {
					throw new MessageTypeException("Attempted to write null");
				}
				packer.writeNil();
				return;
			}
			throw new MessageTypeException("Target is not a List but "
					+ target.getClass());
		}
		packer.writeArrayHeader(target.size());
		for (E e : target) {
			elementPackerTemplate.write(packer, e);
		}
	}
}
