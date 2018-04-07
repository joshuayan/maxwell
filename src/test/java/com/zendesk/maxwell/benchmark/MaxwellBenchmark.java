package com.zendesk.maxwell.benchmark;

import com.zendesk.maxwell.Maxwell;
import com.zendesk.maxwell.MaxwellConfig;
import com.zendesk.maxwell.MaxwellTestSupport;
import com.zendesk.maxwell.MysqlIsolatedServer;
import com.zendesk.maxwell.replication.Position;

import java.sql.*;
import java.util.Random;
import java.util.UUID;

public class MaxwellBenchmark {
	/*
	CREATE TABLE `sharded` (
		`id` bigint(20) NOT NULL AUTO_INCREMENT,
  		`account_id` int(11) UNSIGNED NOT NULL,
  		`nice_id` int(11) NOT NULL,
  		`status_id` tinyint NOT NULL default 2,
		`date_field` datetime,
		`text_field` text,
		`latin1_field` varchar(96) CHARACTER SET latin1 NOT NULL DEFAULT '',
		`utf8_field` varchar(96) CHARACTER SET utf8 NOT NULL DEFAULT '',
		`float_field` float(5,2),
  		`timestamp_field` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  		`timestamp2_field` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  		`datetime2_field` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  		-- Can't have a default time
		-- See http://dev.mysql.com/doc/refman/5.6/en/timestamp-initialization.html
		`time2_field` time(6),
  		`decimal_field` decimal(12,7),
  */

	static Random rand = new Random();

	private static void generateTX(Connection cx, int rowsInTX) throws SQLException {
		PreparedStatement ps = cx.prepareStatement(
			"INSERT INTO shard_1.sharded (id, account_id, nice_id, status_id, date_field, text_field) VALUES(?, ?, ?, ?, ?, ?)"
		);

		for ( int i = 0; i < rowsInTX; i++) {
			ps.setObject(1, null);
			ps.setInt(2, rand.nextInt(1000000));
			ps.setInt(3, rand.nextInt(1000000));
			ps.setInt(4, rand.nextInt(128));
			ps.setDate(5, new Date(rand.nextInt()));
			ps.setString(6, UUID.randomUUID().toString());
			ps.addBatch();
			ps.clearParameters();
		}
		ps.executeBatch();
	}

	private static void generateData(Connection cx, long nRows) throws SQLException {
		while ( nRows > 0 ) {
			int toGenerate = rand.nextInt(50) + 1;
			generateTX(cx, toGenerate);
			nRows -= toGenerate;
		}
	}


	public static void main(String args[]) throws Exception {
		MaxwellConfig config = new MaxwellConfig(args);
		MysqlIsolatedServer server = MaxwellTestSupport.setupServer("");
		MaxwellTestSupport.setupSchema(server, false);

		config.initPosition = Position.capture(server.getConnection(), false);

		config.maxwellMysql.host = "127.0.0.1";
		config.maxwellMysql.port = server.getPort();
		config.maxwellMysql.user = "root";
		config.maxwellMysql.password = "";
		config.replicationMysql = config.schemaMysql = config.maxwellMysql;


		config.producerType = "profiler";

		System.out.println("Generating data...");
		generateData(server.getConnection(), 5000000);
		System.out.println("done generating data, starting maxwell");
		Maxwell m = new Maxwell(config);
		m.run();
	}
}
