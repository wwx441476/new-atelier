package com.yonyougov.atelier.infra.persistence.service;

import com.yonyougov.atelier.infra.datasource.DataSourceConfig;
import com.yonyougov.atelier.infra.datasource.DbType;
import com.yonyougov.atelier.infra.exception.AtelierException;
import com.yonyougov.atelier.infra.persistence.DataSourceJpaTestConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Optional;

@RunWith(SpringRunner.class)
@DataJpaTest
@Import({DataSourcePersistenceService.class, DataSourceJpaTestConfig.class})
public class DataSourcePersistenceServiceTest {

    @Autowired
    private DataSourcePersistenceService service;

    @Test
    public void shouldSaveAndFindDatasource() {
        DataSourceConfig config = DataSourceConfig.builder()
                .id("ds-unit")
                .name("Unit Test H2")
                .jdbcUrl("jdbc:h2:mem:unit;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .dbType(DbType.H2)
                .enabled(true)
                .build();

        service.save(config);

        Optional<DataSourceConfig> loaded = service.findConfigById("ds-unit");
        Assert.assertTrue(loaded.isPresent());
        Assert.assertEquals("Unit Test H2", loaded.get().getName());
        Assert.assertEquals(DbType.H2, loaded.get().getDbType());
    }

    @Test
    public void shouldUpdateExistingDatasource() {
        DataSourceConfig config = DataSourceConfig.builder()
                .id("ds-update")
                .name("Before")
                .jdbcUrl("jdbc:h2:mem:upd;DB_CLOSE_DELAY=-1")
                .username("sa")
                .dbType(DbType.H2)
                .build();
        service.save(config);

        config.setName("After");
        service.save(config);

        Assert.assertEquals("After", service.findConfigById("ds-update").get().getName());
    }

    @Test
    public void shouldListOnlyEnabledDatasources() {
        service.save(DataSourceConfig.builder()
                .id("ds-on")
                .name("On")
                .jdbcUrl("jdbc:h2:mem:on;DB_CLOSE_DELAY=-1")
                .username("sa")
                .dbType(DbType.H2)
                .enabled(true)
                .build());
        service.save(DataSourceConfig.builder()
                .id("ds-off")
                .name("Off")
                .jdbcUrl("jdbc:h2:mem:off;DB_CLOSE_DELAY=-1")
                .username("sa")
                .dbType(DbType.H2)
                .enabled(false)
                .build());

        List<DataSourceConfig> enabled = service.findAllEnabledConfigs();
        Assert.assertEquals(1, enabled.size());
        Assert.assertEquals("ds-on", enabled.get(0).getId());
    }

    @Test(expected = AtelierException.class)
    public void shouldRejectMissingJdbcUrl() {
        service.save(DataSourceConfig.builder().id("bad").name("Bad").build());
    }
}
