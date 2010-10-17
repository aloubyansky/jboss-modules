/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.modules;

import java.util.Set;

/**
 * A dependency specification that represents a single dependency for a module.  The dependency can be on a local loader
 * or another module, or on the target module's local loader.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:jbailey@redhat.com">John Bailey</a>
 * @author Jason T. Greene
 */
public abstract class DependencySpec {

    final PathFilter importFilter;
    final PathFilter exportFilter;

    DependencySpec(final PathFilter importFilter, final PathFilter exportFilter) {
        this.importFilter = importFilter;
        this.exportFilter = exportFilter;
    }

    abstract Dependency getDependency(final Module module);

    /**
     * Create a dependency on the current module's local resources.  You should have at least one such dependency
     * on any module which has its own resources.
     *
     * @return the dependency spec
     */
    public static DependencySpec createLocalDependencySpec() {
        return createLocalDependencySpec(PathFilters.acceptAll(), PathFilters.getDefaultExportFilter());
    }

    /**
     * Create a dependency on the current module's local resources.  You should have at least one such dependency
     * on any module which has its own resources.
     *
     * @param importFilter the import filter to apply
     * @param exportFilter the export filter to apply
     * @return the dependency spec
     */
    public static DependencySpec createLocalDependencySpec(final PathFilter importFilter, final PathFilter exportFilter) {
        if (importFilter == null) {
            throw new IllegalArgumentException("importFilter is null");
        }
        if (exportFilter == null) {
            throw new IllegalArgumentException("exportFilter is null");
        }
        return new DependencySpec(importFilter, exportFilter) {
            Dependency getDependency(final Module module) {
                final ModuleClassLoader classLoader = module.getClassLoaderPrivate();
                return new LocalDependency(exportFilter, importFilter, classLoader.getLocalLoader(), classLoader.getPaths());
            }

            public String toString() {
                return "dependency on local resources";
            }
        };
    }

    /**
     * Create a dependency on the given local loader.
     *
     * @param localLoader the local loader
     * @param loaderPaths the set of paths that is exposed by the local loader
     * @return the dependency spec
     */
    public static DependencySpec createLocalDependencySpec(final LocalLoader localLoader, final Set<String> loaderPaths) {
        return createLocalDependencySpec(PathFilters.acceptAll(), PathFilters.rejectAll(), localLoader, loaderPaths);
    }

    /**
     * Create a dependency on the given local loader.
     *
     * @param localLoader the local loader
     * @param loaderPaths the set of paths that is exposed by the local loader
     * @param export {@code true} if this is a fully re-exported dependency, {@code false} if it should not be exported
     * @return the dependency spec
     */
    public static DependencySpec createLocalDependencySpec(final LocalLoader localLoader, final Set<String> loaderPaths, boolean export) {
        return createLocalDependencySpec(PathFilters.acceptAll(), export ? PathFilters.getDefaultExportFilter() : PathFilters.rejectAll(), localLoader, loaderPaths);
    }

    /**
     * Create a dependency on the given local loader.
     *
     * @param importFilter the import filter to apply
     * @param exportFilter the export filter to apply
     * @param localLoader the local loader
     * @param loaderPaths the set of paths that is exposed by the local loader
     * @return the dependency spec
     */
    public static DependencySpec createLocalDependencySpec(final PathFilter importFilter, final PathFilter exportFilter, final LocalLoader localLoader, final Set<String> loaderPaths) {
        if (importFilter == null) {
            throw new IllegalArgumentException("importFilter is null");
        }
        if (exportFilter == null) {
            throw new IllegalArgumentException("exportFilter is null");
        }
        if (localLoader == null) {
            throw new IllegalArgumentException("localLoader is null");
        }
        if (loaderPaths == null) {
            throw new IllegalArgumentException("loaderPaths is null");
        }
        return new DependencySpec(importFilter, exportFilter) {
            Dependency getDependency(final Module module) {
                return new LocalDependency(exportFilter, importFilter, localLoader, loaderPaths);
            }

            public String toString() {
                return "dependency on local loader " + localLoader;
            }
        };
    }

    /**
     * Create a dependency on the given module.
     *
     * @param identifier the module identifier
     * @return the dependency spec
     */
    public static DependencySpec createModuleDependencySpec(final ModuleIdentifier identifier) {
        return createModuleDependencySpec(identifier, false);
    }

    /**
     * Create a dependency on the given module.
     *
     * @param identifier the module identifier
     * @return the dependency spec
     */
    public static DependencySpec createModuleDependencySpec(final ModuleIdentifier identifier, final boolean export) {
        return createModuleDependencySpec(identifier, export, false);
    }

    /**
     * Create a dependency on the given module.
     *
     * @param identifier the module identifier
     * @param export {@code true} if this is a fully re-exported dependency, {@code false} if it should not be exported
     * @param optional {@code true} if the dependency is optional, {@code false} if it is mandatory
     * @return the dependency spec
     */
    public static DependencySpec createModuleDependencySpec(final ModuleIdentifier identifier, final boolean export, final boolean optional) {
        return createModuleDependencySpec(PathFilters.acceptAll(), export ? PathFilters.acceptAll() : PathFilters.rejectAll(), null, identifier, optional);
    }

    /**
     * Create a dependency on the given module.
     *
     * @param moduleLoader the specific module loader from which the module should be acquired
     * @param identifier the module identifier
     * @param export {@code true} if this is a fully re-exported dependency, {@code false} if it should not be exported
     * @return the dependency spec
     */
    public static DependencySpec createModuleDependencySpec(final ModuleLoader moduleLoader, final ModuleIdentifier identifier, final boolean export) {
        return createModuleDependencySpec(PathFilters.acceptAll(), export ? PathFilters.acceptAll() : PathFilters.rejectAll(), moduleLoader, identifier, false);
    }

    /**
     * Create a dependency on the given module.
     *
     * @param moduleLoader the specific module loader from which the module should be acquired
     * @param identifier the module identifier
     * @param export {@code true} if this is a fully re-exported dependency, {@code false} if it should not be exported
     * @param optional {@code true} if the dependency is optional, {@code false} if it is mandatory
     * @return the dependency spec
     */
    public static DependencySpec createModuleDependencySpec(final ModuleLoader moduleLoader, final ModuleIdentifier identifier, final boolean export, final boolean optional) {
        return createModuleDependencySpec(PathFilters.acceptAll(), export ? PathFilters.acceptAll() : PathFilters.rejectAll(), moduleLoader, identifier, optional);
    }

    /**
     * Create a dependency on the given module.
     *
     * @param exportFilter the export filter to apply
     * @param identifier the module identifier
     * @param optional {@code true} if the dependency is optional, {@code false} if it is mandatory
     * @return the dependency spec
     */
    public static DependencySpec createModuleDependencySpec(final PathFilter exportFilter, final ModuleIdentifier identifier, final boolean optional) {
        return createModuleDependencySpec(PathFilters.acceptAll(), exportFilter, null, identifier, optional);
    }

    /**
     * Create a dependency on the given module.
     *
     * @param exportFilter the export filter to apply
     * @param moduleLoader the specific module loader from which the module should be acquired
     * @param identifier the module identifier
     * @param optional {@code true} if the dependency is optional, {@code false} if it is mandatory
     * @return the dependency spec
     */
    public static DependencySpec createModuleDependencySpec(final PathFilter exportFilter, final ModuleLoader moduleLoader, final ModuleIdentifier identifier, final boolean optional) {
        return createModuleDependencySpec(PathFilters.acceptAll(), exportFilter, moduleLoader, identifier, optional);
    }

    /**
     * Create a dependency on the given module.
     *
     * @param importFilter the import filter to apply
     * @param exportFilter the export filter to apply
     * @param moduleLoader the specific module loader from which the module should be acquired
     * @param identifier the module identifier
     * @param optional {@code true} if the dependency is optional, {@code false} if it is mandatory
     * @return the dependency spec
     */
    public static DependencySpec createModuleDependencySpec(final PathFilter importFilter, final PathFilter exportFilter, final ModuleLoader moduleLoader, final ModuleIdentifier identifier, final boolean optional) {
        if (importFilter == null) {
            throw new IllegalArgumentException("importFilter is null");
        }
        if (exportFilter == null) {
            throw new IllegalArgumentException("exportFilter is null");
        }
        if (identifier == null) {
            throw new IllegalArgumentException("identifier is null");
        }
        return new DependencySpec(importFilter, exportFilter) {
            Dependency getDependency(final Module module) {
                final ModuleLoader loader = moduleLoader;
                return new ModuleDependency(exportFilter, importFilter, loader == null ? module.getModuleLoader() : loader, identifier, optional);
            }

            public String toString() {
                return "dependency on " + identifier;
            }
        };
    }
}
