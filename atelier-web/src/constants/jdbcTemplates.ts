/** JDBC URL templates keyed by dbType (matches backend DbType enum). */
export const JDBC_URL_TEMPLATES: Record<string, string> = {
  MYSQL: 'jdbc:mysql://host:3306/db',
  ORACLE: 'jdbc:oracle:thin:@host:1521:orcl',
  DM: 'jdbc:dm://host:5236/db',
  KINGBASE: 'jdbc:kingbase8://host:54321/db',
  POSTGRESQL: 'jdbc:postgresql://host:5432/db',
  H2: 'jdbc:h2:mem:dbname',
  SQLSERVER: 'jdbc:sqlserver://host:1433;databaseName=db',
  DB2: 'jdbc:db2://host:50000/db',
  STARROCKS: 'jdbc:mysql://host:9030/db',
};

const TEMPLATE_VALUES = new Set(Object.values(JDBC_URL_TEMPLATES));

export function getJdbcTemplate(dbType: string | undefined): string {
  if (!dbType) {
    return JDBC_URL_TEMPLATES.H2;
  }
  return JDBC_URL_TEMPLATES[dbType] ?? JDBC_URL_TEMPLATES.MYSQL;
}

/** True when empty or still an unmodified template value. */
export function isJdbcTemplate(url: string | undefined | null): boolean {
  if (!url || url.trim() === '') {
    return true;
  }
  return TEMPLATE_VALUES.has(url.trim());
}
