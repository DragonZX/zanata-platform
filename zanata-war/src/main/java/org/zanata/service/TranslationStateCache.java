/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.service;

import org.apache.lucene.search.Filter;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;

/**
 * Defines a Cache Service for translation states.
 *
 * @author Carlos Munoz <a href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
public interface TranslationStateCache
{
   /**
    * Returns a Lucene Filter which only returns {@link org.zanata.model.HTextFlow}s which have been translated
    * for the given Locale Id
    * @param targetLocale
    * @return
    */
   Filter getFilter(LocaleId localeId);

   /**
    * Informs the cache that a text flow has changed its state in a given locale.
    * (It's really a Text Flow Target state)
    *
    * @param textFlowId The id of the text flow that has changed state.
    * @param localeId The locale for which state has changed.
    * @param newState The new state after the change.
    */
   void textFlowStateUpdated(Long textFlowId, LocaleId localeId, ContentState newState);
}