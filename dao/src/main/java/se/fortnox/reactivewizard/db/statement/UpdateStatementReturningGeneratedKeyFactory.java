package se.fortnox.reactivewizard.db.statement;

import reactor.core.publisher.FluxSink;
import se.fortnox.reactivewizard.db.GeneratedKey;
import se.fortnox.reactivewizard.db.deserializing.DbResultSetDeserializer;
import se.fortnox.reactivewizard.db.query.ParameterizedQuery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class UpdateStatementReturningGeneratedKeyFactory extends AbstractUpdateStatementFactory {

    private final DbResultSetDeserializer deserializer;

    public UpdateStatementReturningGeneratedKeyFactory(ParameterizedQuery parameterizedQuery,
                                                       Class<?> keyType, int minimumAffected
    ) {
        super(minimumAffected, parameterizedQuery);
        this.deserializer = new DbResultSetDeserializer(keyType);
    }

    @Override
    protected void executeStatement(Connection connection, Object[] args, FluxSink fluxSink)
            throws SQLException {
        try (PreparedStatement statement = parameterizedQuery.createStatement(connection, args, Statement.RETURN_GENERATED_KEYS)) {
            parameterizedQuery.addParameters(args, statement);
            ensureMinimumReached(statement.executeUpdate());
            try (ResultSet resultSet = statement.getGeneratedKeys()) {
                while (resultSet.next()) {
                    if (fluxSink != null) {
                        fluxSink.next((GeneratedKey) () -> deserializer.deserialize(resultSet));
                    }
                }
            }
        }
    }
}
