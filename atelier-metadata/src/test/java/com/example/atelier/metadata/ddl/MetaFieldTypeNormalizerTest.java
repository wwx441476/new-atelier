package com.example.atelier.metadata.ddl;

import org.junit.Assert;
import org.junit.Test;

public class MetaFieldTypeNormalizerTest {

    @Test
    public void shouldNormalizeH2CharacterVaryingToVarchar() {
        Assert.assertEquals("VARCHAR", MetaFieldTypeNormalizer.normalize("CHARACTER VARYING"));
    }

    @Test
    public void shouldNormalizeIntegerTypes() {
        Assert.assertEquals("INTEGER", MetaFieldTypeNormalizer.normalize("INTEGER"));
        Assert.assertEquals("INTEGER", MetaFieldTypeNormalizer.normalize("BIGINT"));
    }

    @Test
    public void shouldKeepDecimal() {
        Assert.assertEquals("DECIMAL", MetaFieldTypeNormalizer.normalize("DECIMAL"));
    }
}
