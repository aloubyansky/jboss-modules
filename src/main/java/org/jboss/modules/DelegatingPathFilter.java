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

/**
 * PathFilter implementation that delegates to other filters.
 * 
 * @author John E. Bailey
 */
public class DelegatingPathFilter implements PathFilter {
    private final PathFilter[] delegates;

    public DelegatingPathFilter(final PathFilter... delegates) {
        this.delegates = delegates;
    }

    @Override
    public boolean accept(String path) {
        for(PathFilter filter : delegates) {
            if(!filter.accept(path))
                return false;
        }
        return true;
    }
}
