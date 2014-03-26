/*
 *
 *  * Copyright 2014, Red Hat, Inc. and individual contributors as indicated by the
 *  * @author tags. See the copyright.txt file in the distribution for a full
 *  * listing of individual contributors.
 *  *
 *  * This is free software; you can redistribute it and/or modify it under the
 *  * terms of the GNU Lesser General Public License as published by the Free
 *  * Software Foundation; either version 2.1 of the License, or (at your option)
 *  * any later version.
 *  *
 *  * This software is distributed in the hope that it will be useful, but WITHOUT
 *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 *  * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 *  * details.
 *  *
 *  * You should have received a copy of the GNU Lesser General Public License
 *  * along with this software; if not, write to the Free Software Foundation,
 *  * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 *  * site: http://www.fsf.org.
 */
package org.zanata.action;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.faces.application.FacesMessage;
import javax.faces.event.ValueChangeEvent;
import javax.persistence.EntityNotFoundException;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;
import org.hibernate.criterion.NaturalIdentifier;
import org.hibernate.criterion.Restrictions;
import org.jboss.seam.Component;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.security.Restrict;
import org.jboss.seam.core.Events;
import org.jboss.seam.faces.FacesMessages;
import org.zanata.annotation.CachedMethodResult;
import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.dao.ProjectDAO;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.seam.scope.FlashScopeMessage;
import org.zanata.service.LocaleService;
import org.zanata.service.SlugEntityService;
import org.zanata.service.ValidationService;
import org.zanata.service.impl.LocaleServiceImpl;
import org.zanata.ui.AbstractAutocomplete;
import org.zanata.ui.FilterUtil;
import org.zanata.util.ComparatorUtil;
import org.zanata.util.ZanataMessages;
import org.zanata.webtrans.shared.model.ValidationAction;
import org.zanata.webtrans.shared.model.ValidationId;
import org.zanata.webtrans.shared.validation.ValidationFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Name("versionHome")
@Slf4j
public class VersionHome extends SlugHome<HProjectIteration> {

    private static final long serialVersionUID = 1L;

    public static final String PROJECT_ITERATION_UPDATE =
            "project.iteration.update";

    @Getter
    @Setter
    private String slug;

    @Getter
    @Setter
    private String projectSlug;

    @In
    private FlashScopeMessage flashScopeMessage;

    @In
    private LocaleService localeServiceImpl;

    @In
    private ValidationService validationServiceImpl;

    @In
    private SlugEntityService slugEntityServiceImpl;

    @In(create = true)
    private ProjectDAO projectDAO;

    @In
    private ZanataMessages zanataMessages;

    private Map<ValidationId, ValidationAction> availableValidations = Maps
            .newHashMap();

    @Getter
    @Setter
    private boolean isNewInstance = false;

    @Getter
    @Setter
    private String selectedProjectType;

    @Getter
    private LocaleAutocomplete localeAutocomplete = new LocaleAutocomplete();

    public void createNew() {
        isNewInstance = true;
    }

    public HProject getProject() {
        return projectDAO.getBySlug(projectSlug);
    }

    @Restrict("#{s:hasPermission(versionHome.instance, 'update')}")
    public void updateCustomisedLocales(String key, boolean checked) {
        getInstance().setOverrideLocales(!checked);
        update();
    }

    @Override
    protected HProjectIteration loadInstance() {
        Session session = (Session) getEntityManager().getDelegate();
        return (HProjectIteration) session.byNaturalId(HProjectIteration.class)
                .using("slug", getSlug())
                .using("project", projectDAO.getBySlug(projectSlug)).load();
    }

    @Restrict("#{s:hasPermission(versionHome.instance, 'update')}")
    public void updateRequireTranslationReview(String key, boolean checked) {
        getInstance().setRequireTranslationReview(checked);
        update();
        if (checked) {
            getFlashScopeMessage().putMessage(
                    FacesMessage.SEVERITY_INFO,
                    zanataMessages
                            .getMessage("jsf.iteration.requireReview.enabled"));
        } else {
            getFlashScopeMessage()
                    .putMessage(
                            FacesMessage.SEVERITY_INFO,
                            zanataMessages
                                    .getMessage("jsf.iteration.requireReview.disabled"));
        }
    }

    public List<ValidationAction> getValidationList() {
        List<ValidationAction> sortedList =
                Lists.newArrayList(getValidations().values());
        Collections.sort(sortedList,
                ValidationFactory.ValidationActionComparator);
        return sortedList;
    }

    private Map<ValidationId, ValidationAction> getValidations() {
        if (availableValidations.isEmpty()) {
            Collection<ValidationAction> validationList =
                    validationServiceImpl.getValidationActions(projectSlug,
                            slug);

            for (ValidationAction validationAction : validationList) {
                availableValidations.put(validationAction.getId(),
                        validationAction);
            }
        }

        return availableValidations;
    }

    public void validateSuppliedId() {
        getInstance(); // this will raise an EntityNotFound exception
        // when id is invalid and conversation will not
        // start
    }

    public ProjectType getProjectType() {
        if (getInstance().getProjectType() == null) {
            getInstance().setProjectType(
                    getInstance().getProject().getDefaultProjectType());
        }
        return getInstance().getProjectType();
    }

    public void setProjectType(ProjectType projectType) {
        getInstance().setProjectType(projectType);
    }

    public void validateProjectSlug() {
        if (projectDAO.getBySlug(projectSlug) == null) {
            throw new EntityNotFoundException("no entity with slug "
                    + projectSlug);
        }
    }

    public void verifySlugAvailable(ValueChangeEvent e) {
        String slug = (String) e.getNewValue();
        validateSlug(slug, e.getComponent().getId());
    }

    public boolean validateSlug(String slug, String componentId) {
        if (!isSlugAvailable(slug)) {
            FacesMessages.instance().addToControl(componentId,
                    "This Version ID has been used in this project");
            return false;
        }
        return true;
    }

    public boolean isSlugAvailable(String slug) {
        return slugEntityServiceImpl.isProjectIterationSlugAvailable(slug,
                projectSlug);
    }

    @Override
    public String persist() {
        getFlashScopeMessage().clearMessages();
        updateProjectType();

        if (!validateSlug(getInstance().getSlug(), "slug"))
            return null;

        HProject project = getProject();
        project.addIteration(getInstance());

        List<HLocale> projectLocales =
                localeServiceImpl.getSupportedLanguageByProject(projectSlug);

        getInstance().getCustomizedLocales().addAll(projectLocales);

        getInstance().getCustomizedValidations().putAll(
                project.getCustomizedValidations());

        return super.persist();
    }

    @Override
    public Object getId() {
        return projectSlug + "/" + slug;
    }

    @Override
    public NaturalIdentifier getNaturalId() {
        return Restrictions.naturalId().set("slug", slug)
                .set("project", projectDAO.getBySlug(projectSlug));
    }

    @Override
    public boolean isIdDefined() {
        return slug != null && projectSlug != null;
    }

    @CachedMethodResult
    public List<HLocale> getInstanceActiveLocales() {
        if (StringUtils.isNotEmpty(projectSlug) && StringUtils.isNotEmpty(slug)) {
            List<HLocale> locales =
                    localeServiceImpl.getSupportedLanguageByProjectIteration(
                            projectSlug, slug);
            Collections.sort(locales, ComparatorUtil.LOCALE_COMPARATOR);
            return locales;
        }
        return localeServiceImpl.getSupportedAndEnabledLocales();
    }

    public boolean isValidationsSameAsProject() {
        return getInstance().getCustomizedValidations().equals(
                getInstance().getProject().getCustomizedValidations());
    }

    public void copyValidationFromProject() {
        getInstance().getCustomizedValidations().clear();
        getInstance().getCustomizedValidations().putAll(
                getInstance().getProject().getCustomizedValidations());
        availableValidations.clear();
        update();

        getFlashScopeMessage()
                .putMessage(
                        FacesMessage.SEVERITY_INFO,
                        zanataMessages
                                .getMessage("jsf.iteration.CopyProjectValidations.message"));
    }

    @Override
    @Restrict("#{s:hasPermission(versionHome.instance, 'update')}")
    public String update() {
        getFlashScopeMessage().clearMessages();
        String state = super.update();
        Events.instance().raiseEvent(PROJECT_ITERATION_UPDATE, getInstance());
        return state;
    }

    @Restrict("#{s:hasPermission(versionHome.instance, 'update')}")
    public void removeLanguage(LocaleId localeId) {
        HLocale locale = localeServiceImpl.getByLocaleId(localeId);
        getInstance().getCustomizedLocales().remove(locale);

        update();
        getFlashScopeMessage().putMessage(
                FacesMessage.SEVERITY_INFO,
                zanataMessages.getMessage("jsf.iteration.LanguageRemoved",
                        locale.retrieveDisplayName()));
    }

    @Restrict("#{s:hasPermission(versionHome.instance, 'update')}")
    public void updateStatus(char initial) {
        getInstance().setStatus(EntityStatus.valueOf(initial));
        update();
        getFlashScopeMessage().putMessage(
                FacesMessage.SEVERITY_INFO,
                zanataMessages.getMessage("jsf.iteration.status.updated",
                        EntityStatus.valueOf(initial)));
    }

    public void updateSelectedProjectType(ValueChangeEvent e) {
        selectedProjectType = (String) e.getNewValue();
        updateProjectType();
    }

    public void copyProjectTypeFromProject() {
        getInstance().setProjectType(
                getInstance().getProject().getDefaultProjectType());
        update();
        getFlashScopeMessage().putMessage(
                FacesMessage.SEVERITY_INFO,
                zanataMessages
                        .getMessage("jsf.iteration.CopyProjectType.message"));
    }

    private void updateProjectType() {
        if (!StringUtils.isEmpty(selectedProjectType)
                && !selectedProjectType.equals("null")) {
            ProjectType projectType = ProjectType.valueOf(selectedProjectType);
            getInstance().setProjectType(projectType);
        } else {
            getInstance().setProjectType(null);
        }
    }

    public List<ValidationAction.State> getValidationStates() {
        return Arrays.asList(ValidationAction.State.values());
    }

    @Restrict("#{s:hasPermission(versionHome.instance, 'update')}")
    public void updateValidationOption(String name, String state) {
        ValidationId validatationId = ValidationId.valueOf(name);

        for (Map.Entry<ValidationId, ValidationAction> entry : getValidations()
                .entrySet()) {
            if (entry.getKey().name().equals(name)) {
                getValidations().get(validatationId).setState(
                        ValidationAction.State.valueOf(state));
                getInstance().getCustomizedValidations().put(
                        entry.getKey().name(),
                        entry.getValue().getState().name());
                ensureMutualExclusivity(getValidations().get(validatationId));
                break;
            }
        }
        update();
        getFlashScopeMessage().putMessage(
                FacesMessage.SEVERITY_INFO,
                zanataMessages.getMessage("jsf.validation.updated",
                        validatationId.getDisplayName(), state));
    }

    /**
     * If this action is enabled(Warning or Error), then it's exclusive
     * validation will be turn off
     *
     */
    private void ensureMutualExclusivity(
            ValidationAction selectedValidationAction) {
        if (selectedValidationAction.getState() != ValidationAction.State.Off) {
            for (ValidationAction exclusiveValAction : selectedValidationAction
                    .getExclusiveValidations()) {
                getInstance().getCustomizedValidations().put(
                        exclusiveValAction.getId().name(),
                        ValidationAction.State.Off.name());
                getValidations().get(exclusiveValAction.getId()).setState(
                        ValidationAction.State.Off);
            }
        }
    }

    private FlashScopeMessage getFlashScopeMessage() {
        if (flashScopeMessage == null) {
            flashScopeMessage = FlashScopeMessage.instance();
        }
        return flashScopeMessage;
    }

    private class LocaleAutocomplete extends AbstractAutocomplete<HLocale> {

        private LocaleService localeServiceImpl = (LocaleService) Component
                .getInstance(LocaleServiceImpl.class);

        private ZanataMessages zanataMessages = (ZanataMessages) Component
                .getInstance(ZanataMessages.class);

        public List<HLocale> getInstanceActiveLocales() {
            if (StringUtils.isNotEmpty(projectSlug)
                    && StringUtils.isNotEmpty(slug)) {
                List<HLocale> locales =
                        localeServiceImpl
                                .getSupportedLanguageByProjectIteration(
                                        projectSlug, slug);
                Collections.sort(locales, ComparatorUtil.LOCALE_COMPARATOR);
                return locales;
            }
            return localeServiceImpl.getSupportedAndEnabledLocales();
        }

        /**
         * Return results on search
         */
        @Override
        public List<HLocale> suggest() {
            List<HLocale> localeList = localeServiceImpl.getSupportedLocales();

            Collection<HLocale> filtered =
                    Collections2.filter(localeList, new Predicate<HLocale>() {
                        @Override
                        public boolean apply(@Nullable HLocale input) {
                            return FilterUtil.isIncludeLocale(
                                    getInstanceActiveLocales(), input,
                                    getQuery());
                        }
                    });
            return Lists.newArrayList(filtered);
        }

        /**
         * Action when an item is selected
         */
        @Override
        public void onSelectItemAction() {
            if (StringUtils.isEmpty(getSelectedItem())) {
                return;
            }
            HLocale locale = localeServiceImpl.getByLocaleId(getSelectedItem());
            getInstance().getCustomizedLocales().add(locale);

            update();
            reset();

            getFlashScopeMessage().putMessage(
                    FacesMessage.SEVERITY_INFO,
                    zanataMessages.getMessage("jsf.iteration.LanguageAdded",
                            locale.retrieveDisplayName()));
        }
    }

    public List<ProjectType> getProjectTypeList() {
        List<ProjectType> projectTypes = Arrays.asList(ProjectType.values());
        Collections.sort(projectTypes, ComparatorUtil.PROJECT_TYPE_COMPARATOR);
        return projectTypes;
    }
}
