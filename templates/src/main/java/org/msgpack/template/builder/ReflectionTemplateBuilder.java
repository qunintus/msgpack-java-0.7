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
package org.msgpack.template.builder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.msgpack.MessageTypeException;
import org.msgpack.packer.Packer;
import org.msgpack.template.PackerTemplate;
import org.msgpack.template.AbstractPackerTemplate;
import org.msgpack.template.AbstractUnpackerTemplate;
import org.msgpack.template.TemplateRegistry;
import org.msgpack.template.UnpackerTemplate;
import org.msgpack.unpacker.Unpacker;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ReflectionTemplateBuilder extends AbstractTemplateBuilder {

    private static Logger LOG = Logger.getLogger(ReflectionBeansTemplateBuilder.class.getName());

    protected static abstract class ReflectionFieldPackerTemplate extends AbstractPackerTemplate<Object> {
        protected FieldEntry entry;

        ReflectionFieldPackerTemplate(final FieldEntry entry) {
            this.entry = entry;
        }

        void setNil(Object v) {
            entry.set(v, null);
        }
    }

    static final class FieldPackerTemplateImpl extends ReflectionFieldPackerTemplate {
        private PackerTemplate packerTemplate;

        public FieldPackerTemplateImpl(final FieldEntry entry, final PackerTemplate packerTemplate) {
            super(entry);
            this.packerTemplate = packerTemplate;
        }

        @Override
        public void write(Packer packer, Object v, boolean required)
                throws IOException {
        	packerTemplate.write(packer, v, required);
        }
    }

    protected static abstract class ReflectionFieldUnpackerTemplate extends AbstractUnpackerTemplate<Object> {
        protected FieldEntry entry;

        ReflectionFieldUnpackerTemplate(final FieldEntry entry) {
            this.entry = entry;
        }

        void setNil(Object v) {
            entry.set(v, null);
        }
    }

	static final class FieldUnpackerTemplateImpl extends
			ReflectionFieldUnpackerTemplate {
		private UnpackerTemplate unpackerTemplate;

		public FieldUnpackerTemplateImpl(final FieldEntry entry,
				final UnpackerTemplate unpackerTemplate) {
			super(entry);
			this.unpackerTemplate = unpackerTemplate;
		}

		@Override
		public Object read(Unpacker unpacker, Object to, boolean required)
				throws IOException {
			// Class<Object> type = (Class<Object>) entry.getType();
			Object f = entry.get(to);
			Object o = unpackerTemplate.read(unpacker, f, required);
			if (o != f) {
				entry.set(to, o);
			}
			return o;
		}
	}

	protected static class ReflectionClassPackerTemplate<T> extends
			AbstractPackerTemplate<T> {
		protected Class<T> targetClass;

		protected ReflectionFieldPackerTemplate[] packerTemplates;

		protected ReflectionClassPackerTemplate(Class<T> targetClass,
				ReflectionFieldPackerTemplate[] packerTemplates) {
			this.targetClass = targetClass;
			this.packerTemplates = packerTemplates;
		}

		@Override
		public void write(Packer packer, T target, boolean required)
				throws IOException {
			if (target == null) {
				if (required) {
					throw new MessageTypeException("attempted to write null");
				}
				packer.writeNil();
				return;
			}
			try {
				packer.writeArrayHeader(packerTemplates.length);
				for (ReflectionFieldPackerTemplate tmpl : packerTemplates) {
					if (!tmpl.entry.isAvailable()) {
						packer.writeNil();
						continue;
					}
					Object obj = tmpl.entry.get(target);
					if (obj == null) {
						if (tmpl.entry.isNotNullable()) {
							throw new MessageTypeException(tmpl.entry.getName()
									+ " cannot be null by @NotNullable");
						}
						packer.writeNil();
					} else {
						tmpl.write(packer, obj, true);
					}
				}
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new MessageTypeException(e);
			}
		}
	}

	protected static class ReflectionClassUnpackerTemplate<T> extends
			AbstractUnpackerTemplate<T> {
		protected Class<T> targetClass;

		protected ReflectionFieldUnpackerTemplate[] unpackerTemplates;

		protected ReflectionClassUnpackerTemplate(Class<T> targetClass,
				ReflectionFieldUnpackerTemplate[] unpackerTemplates) {
			this.targetClass = targetClass;
			this.unpackerTemplates = unpackerTemplates;
		}

		@Override
		public T read(Unpacker unpacker, T to, boolean required)
				throws IOException {
			if (!required && unpacker.trySkipNil()) {
				return null;
			}
			try {
				if (to == null) {
					to = targetClass.newInstance();
				}

				unpacker.readArrayHeader();
				for (int i = 0; i < unpackerTemplates.length; i++) {
					ReflectionFieldUnpackerTemplate tmpl = unpackerTemplates[i];
					if (!tmpl.entry.isAvailable()) {
						unpacker.skip();
					} else if (tmpl.entry.isOptional() && unpacker.trySkipNil()) {
						// if Optional + nil, than keep default value
					} else {
						tmpl.read(unpacker, to, false);
					}
				}

				return to;
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new MessageTypeException(e);
			}
		}
	}

    public ReflectionTemplateBuilder(TemplateRegistry registry) {
        super(registry);
    }

    @Override
    public boolean matchType(Type targetType, boolean hasAnnotation) {
        Class<?> targetClass = (Class<?>) targetType;
        boolean matched = matchAtClassTemplateBuilder(targetClass, hasAnnotation);
        if (matched && LOG.isLoggable(Level.FINE)) {
            LOG.fine("matched type: " + targetClass.getName());
        }
        return matched;
    }

    @Override
    public <T> PackerTemplate<T> buildPackerTemplate(Class<T> targetClass, FieldEntry[] entries) {
        if (entries == null) {
            throw new NullPointerException("entries is null: " + targetClass);
        }

        ReflectionFieldPackerTemplate[] packerTemplates = toPackerTemplates(entries);
        return new ReflectionClassPackerTemplate<T>(targetClass, packerTemplates);
    }

	protected ReflectionFieldPackerTemplate[] toPackerTemplates(
			FieldEntry[] entries) {
		// TODO Now it is simply cast. #SF
		for (FieldEntry entry : entries) {
			Field field = ((DefaultFieldEntry) entry).getField();
			int mod = field.getModifiers();
			if (!Modifier.isPublic(mod)) {
				field.setAccessible(true);
			}
		}

		ReflectionFieldPackerTemplate[] packerTemplates = new ReflectionFieldPackerTemplate[entries.length];
		for (int i = 0; i < entries.length; i++) {
			FieldEntry entry = entries[i];
			// Class<?> t = entry.getType();
			PackerTemplate packerTemplate = registry.lookupPackerTemplate(entry
					.getGenericType());
			packerTemplates[i] = new FieldPackerTemplateImpl(entry,
					packerTemplate);
		}
		return packerTemplates;
	}

	@Override
	public <T> UnpackerTemplate<T> buildUnpackerTemplate(Class<T> targetClass,
			FieldEntry[] entries) {
		if (entries == null) {
			throw new NullPointerException("entries is null: " + targetClass);
		}

		ReflectionFieldUnpackerTemplate[] unpackerTemplates = toUnpackerTemplates(entries);
		return new ReflectionClassUnpackerTemplate<T>(targetClass,
				unpackerTemplates);
	}

	protected ReflectionFieldUnpackerTemplate[] toUnpackerTemplates(
			FieldEntry[] entries) {
		// TODO Now it is simply cast. #SF
		for (FieldEntry entry : entries) {
			Field field = ((DefaultFieldEntry) entry).getField();
			int mod = field.getModifiers();
			if (!Modifier.isPublic(mod)) {
				field.setAccessible(true);
			}
		}

		ReflectionFieldUnpackerTemplate[] unpackerTemplates = new ReflectionFieldUnpackerTemplate[entries.length];
		for (int i = 0; i < entries.length; i++) {
			FieldEntry entry = entries[i];
			// Class<?> t = entry.getType();
			UnpackerTemplate unpackerTemplate = registry
					.lookupUnpackerTemplate(entry.getGenericType());
			unpackerTemplates[i] = new FieldUnpackerTemplateImpl(entry,
					unpackerTemplate);
		}
		return unpackerTemplates;
	}
}