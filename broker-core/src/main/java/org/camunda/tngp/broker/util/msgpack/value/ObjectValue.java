package org.camunda.tngp.broker.util.msgpack.value;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.util.msgpack.property.BaseProperty;
import org.camunda.tngp.broker.util.msgpack.property.UndeclaredProperty;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

public class ObjectValue extends BaseValue
{
    private final List<BaseProperty<? extends BaseValue>> declaredProperties = new ArrayList<>();
    private final List<UndeclaredProperty> undeclaredProperties = new ArrayList<>();
    private final List<UndeclaredProperty> recycledProperties = new ArrayList<>();

    private final StringValue decodedKey = new StringValue();

    public ObjectValue declareProperty(BaseProperty<? extends BaseValue> prop)
    {
        declaredProperties.add(prop);
        return this;
    }

    @Override
    public void reset()
    {
        for (int i = 0; i < declaredProperties.size(); ++i)
        {
            final BaseProperty<? extends BaseValue> prop = declaredProperties.get(i);
            prop.reset();
        }

        for (int i = undeclaredProperties.size() - 1; i >= 0; --i)
        {
            final UndeclaredProperty undeclaredProperty = undeclaredProperties.remove(i);
            undeclaredProperty.reset();
            recycledProperties.add(undeclaredProperty);
        }
    }

    private UndeclaredProperty newUndeclaredProperty(StringValue key)
    {
        final int recycledSize = recycledProperties.size();

        UndeclaredProperty prop = null;

        if (recycledSize > 0)
        {
            prop = recycledProperties.remove(recycledSize - 1);
        }
        else
        {
            prop = new UndeclaredProperty();
        }

        prop.getKey().wrap(key);
        undeclaredProperties.add(prop);

        return prop;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append("{");

        writeJson(builder, declaredProperties);
        writeJson(builder, undeclaredProperties);

        builder.append("}");
    }


    protected <T extends BaseProperty<?>> void writeJson(StringBuilder builder, List<T> properties)
    {
        for (int i = 0; i < properties.size(); i++)
        {
            if (i > 0)
            {
                builder.append(",");
            }

            final BaseProperty<? extends BaseValue> prop = properties.get(i);

            if (prop.isWriteable())
            {
                prop.writeJSON(builder);
            }
        }
    }

    @Override
    public void read(MsgPackReader reader)
    {
        final int mapSize = reader.readMapHeader();

        for (int i = 0; i < mapSize; ++i)
        {
            decodedKey.read(reader);

            BaseProperty<? extends BaseValue> prop = null;

            for (int k = 0; k < declaredProperties.size(); ++k)
            {
                final BaseProperty<? extends BaseValue> declaredProperty = declaredProperties.get(k);
                final StringValue declaredKey = declaredProperty.getKey();

                if (declaredKey.equals(decodedKey))
                {
                    prop = declaredProperty;
                    break;
                }
            }

            if (prop == null)
            {
                prop = newUndeclaredProperty(decodedKey);
            }

            prop.read(reader);
        }
    }

    /**
     * Caution: In case not all properties are writeable (i.e. value not set and no default),
     * this method may write some of the values and only then throw an exception.
     * The same exception is raised by {@link #getEncodedLength()}. If you call that first and it succeeds,
     * you are safe to write all the values.
     */
    @Override
    public void write(MsgPackWriter writer)
    {
        final int size = declaredProperties.size() + undeclaredProperties.size();

        writer.writeMapHeader(size);
        write(writer, declaredProperties);
        write(writer, undeclaredProperties);
    }

    protected <T extends BaseProperty<?>> void write(MsgPackWriter writer, List<T> properties)
    {
        for (int i = 0; i < properties.size(); ++i)
        {
            final BaseProperty<? extends BaseValue> prop = properties.get(i);
            prop.write(writer);
        }
    }


    @Override
    public int getEncodedLength()
    {
        final int size = declaredProperties.size() + undeclaredProperties.size();

        int length = MsgPackWriter.getEncodedMapHeaderLenght(size);
        length += getEncodedLength(declaredProperties);
        length += getEncodedLength(undeclaredProperties);

        return length;
    }

    protected <T extends BaseProperty<?>> int getEncodedLength(List<T> properties)
    {
        int length = 0;
        for (int i = 0; i < properties.size(); ++i)
        {
            final T prop = properties.get(i);
            length += prop.getEncodedLength();
        }
        return length;
    }

}
