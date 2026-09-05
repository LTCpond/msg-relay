package com.ltcpond.msgrelay.user.repository;

import com.ltcpond.msgrelay.user.model.entity.LoginDevice;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LoginDeviceMapperTest {
    private LoginDeviceMapper mapper;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        // 使用实际 schema 中的设备表 DDL，保证不再依赖 refresh_token 列。
        String schema = new ClassPathResource("db/schema.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        int start = schema.indexOf("CREATE TABLE IF NOT EXISTS t_login_device");
        jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1));
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mapper/LoginDeviceMapper.xml"));
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(LoginDeviceMapper.class);
    }

    private LoginDevice device(long id, String deviceId) {
        var device = new LoginDevice();
        device.setId(id);
        device.setUserId(1L);
        device.setDeviceId(deviceId);
        device.setDeviceType("PC");
        device.setIp("127.0.0.1");
        device.setLastActiveAt(LocalDateTime.now());
        return device;
    }

    @Test
    void deletedSessionIsInvisibleToAuthenticationAndCanBeRestoredInPlace() {
        mapper.insert(device(1L, "PC-A"));
        assertTrue(mapper.existsByUserIdAndDeviceId(1L, "PC-A"));
        mapper.deleteByUserIdAndDeviceId(1L, "PC-A");
        assertNull(mapper.selectByUserIdAndDeviceId(1L, "PC-A"));
        assertFalse(mapper.existsByUserIdAndDeviceId(1L, "PC-A"));
        assertEquals(0L, mapper.countByUserId(1L));
        var restored = mapper.selectByUserIdAndDeviceIdIncludingDeleted(1L, "PC-A");
        assertEquals(1, restored.getDeleted());
        restored.setIp("127.0.0.2");
        restored.setDeviceType("WEB");
        restored.setLastActiveAt(LocalDateTime.now());
        mapper.updateById(restored);
        assertEquals(1L, mapper.countByUserId(1L));
        var active = mapper.selectByUserIdAndDeviceId(1L, "PC-A");
        assertEquals(1L, active.getId());
        assertEquals("127.0.0.2", active.getIp());
        assertEquals("WEB", active.getDeviceType());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM t_login_device", Integer.class));
    }

    @Test
    void uniqueUserDevicePairAlsoAppliesToDeletedRows() {
        mapper.insert(device(1L, "PC-A"));
        mapper.deleteByUserIdAndDeviceId(1L, "PC-A");
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> mapper.insert(device(2L, "PC-A")));
        var otherUser = device(3L, "PC-A");
        otherUser.setUserId(2L);
        assertEquals(1, mapper.insert(otherUser));
        assertEquals(0L, mapper.countByUserId(1L));
        assertEquals(1L, mapper.countByUserId(2L));
    }
}
