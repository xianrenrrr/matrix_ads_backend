# Backend Development Rules

## API Documentation
Whenever an API is created or modified, add or update its documentation in `api.md`.

## Changelog
Whenever you make a change to the codebase, log the change under today's date in `README.md` under the changelog section.

## DAO Implementation: Firestore Injection

For all DAO implementation classes that use Firestore, always follow this pattern:

**Inject Firestore using Spring's @Autowired annotation**

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ExampleDaoImpl implements ExampleDao {
    @Autowired
    private Firestore db;
    
    // DAO methods...
}
```

This ensures a single, Spring-managed Firestore client is used throughout the application. 

**Important**: Do not manually initialize or close Firestore in DAOs.