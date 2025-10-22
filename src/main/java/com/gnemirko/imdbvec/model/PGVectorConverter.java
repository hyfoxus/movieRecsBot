package com.gnemirko.imdbvec.model;

import com.pgvector.PGvector;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PGVectorConverter implements AttributeConverter<PGvector, Object> {
    @Override
    public Object convertToDatabaseColumn(PGvector attribute) {
        return attribute;
    }

    @Override
    public PGvector convertToEntityAttribute(Object dbData) {
        if (dbData instanceof PGvector v) return v;
        if (dbData instanceof float[] fa) return new PGvector(fa);
        if (dbData instanceof Double[] da) {
            float[] fa = new float[da.length];
            for (int i = 0; i < da.length; i++) fa[i] = da[i].floatValue();
            return new PGvector(fa);
        }
        return null;
    }
}
