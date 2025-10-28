# IAM System Migration Guide

## Overview
This guide helps you migrate existing users to the new IAM (Identity and Access Management) system with role-based permissions.

## What Changed
- Added `role` field to User model (CONTENT_MANAGER or EMPLOYEE)
- Added `createdBy` field to track who created employee accounts
- Added permission checks for template deletion and group management
- Login now returns user role information

## Migration Steps

### 1. Run the Migration Script

The migration script will set all existing users as CONTENT_MANAGER.

```bash
cd matrix_ads_backend
mvn spring-boot:run -Dstart-class=com.example.demo.migration.SetExistingUsersAsManagers
```

Or if using your IDE, run the `SetExistingUsersAsManagers` class directly.

### 2. Verify Migration

Check your Firestore console to verify that all users now have:
- `role: "CONTENT_MANAGER"`
- `createdBy: null`

### 3. Deploy Backend Changes

Deploy the updated backend with the new permission checks:
- Template deletion permission check
- Group management permission check
- Login endpoint returns role

### 4. Deploy Frontend Changes

Deploy the updated mini program with:
- Role storage in login
- Conditional UI rendering based on role
- Employee management page (for content managers only)

## Testing

### Test as Content Manager
1. Login with an existing user account
2. Verify you can:
   - Create/delete templates
   - Manage groups
   - Create employee accounts
   - Delete employee accounts

### Test as Employee
1. Create a new employee account using the employee management page
2. Login with the employee credentials
3. Verify you can:
   - Create templates
   - Push templates
   - Delete own templates (within 2 days)
4. Verify you CANNOT:
   - Delete old templates
   - Delete other users' templates
   - Manage groups
   - Create/delete employee accounts

## Rollback

If you need to rollback, you can remove the role fields:

```javascript
// Run in Firestore console
const users = await db.collection('users').get();
users.forEach(doc => {
  doc.ref.update({
    role: firebase.firestore.FieldValue.delete(),
    createdBy: firebase.firestore.FieldValue.delete()
  });
});
```

## Notes

- The migration is idempotent - it's safe to run multiple times
- Users with existing roles will be skipped
- All existing users become CONTENT_MANAGER by default
- New employee accounts will automatically get EMPLOYEE role
