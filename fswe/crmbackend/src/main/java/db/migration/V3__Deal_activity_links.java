package db.migration;

import org.flywaydb.core.api.migration.*;

public class V3__Deal_activity_links extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 1; }
    @Override public void migrate(Context context) throws Exception {
        try (var statement = context.getConnection().createStatement()) {
            for (String table : new String[]{"interactions", "tasks"}) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN deal_id BIGINT");
                statement.execute("CREATE INDEX ix_" + table + "_deal ON " + table + "(deal_id)");
                statement.execute("ALTER TABLE " + table + " ADD CONSTRAINT fk_" + table + "_deal FOREIGN KEY(deal_id) REFERENCES deals(deal_id)");
            }
        }
    }
}
