/** Role hierarchy - higher index means more privilege */
const ROLE_ORDER = ["GUEST", "USER", "ADMIN", "SUPER_ADMIN"];

/** Check whether the user's role meets or exceeds the required role */
export function hasRole(userRole, requiredRole) {
  return ROLE_ORDER.indexOf(userRole) >= ROLE_ORDER.indexOf(requiredRole);
}
