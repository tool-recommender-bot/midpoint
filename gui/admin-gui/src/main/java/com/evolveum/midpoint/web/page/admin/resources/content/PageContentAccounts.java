/*
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.web.page.admin.resources.content;

import com.evolveum.midpoint.common.QueryUtil;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.holder.XPathHolder;
import com.evolveum.midpoint.schema.holder.XPathSegment;
import com.evolveum.midpoint.schema.processor.ObjectClassComplexTypeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceAttribute;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.button.AjaxSubmitLinkButton;
import com.evolveum.midpoint.web.component.data.TablePanel;
import com.evolveum.midpoint.web.component.data.column.ButtonColumn;
import com.evolveum.midpoint.web.component.data.column.EnumPropertyColumn;
import com.evolveum.midpoint.web.component.data.column.LinkColumn;
import com.evolveum.midpoint.web.component.option.OptionContent;
import com.evolveum.midpoint.web.component.option.OptionItem;
import com.evolveum.midpoint.web.component.option.OptionPanel;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.resources.PageAdminResources;
import com.evolveum.midpoint.web.page.admin.resources.content.dto.AccountContentDataProvider;
import com.evolveum.midpoint.web.page.admin.resources.content.dto.AccountContentDto;
import com.evolveum.midpoint.web.page.admin.resources.content.dto.AccountContentSearchDto;
import com.evolveum.midpoint.web.page.admin.users.PageUser;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_2.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2.ResourceType;
import com.evolveum.prism.xml.ns._public.query_2.QueryType;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.*;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author lazyman
 */
public class PageContentAccounts extends PageAdminResources {

    private static final Trace LOGGER = TraceManager.getTrace(PageContentAccounts.class);
    private IModel<PrismObject<ResourceType>> resourceModel;
    private LoadableModel<AccountContentSearchDto> model;

    public PageContentAccounts() {
        resourceModel = new LoadableModel<PrismObject<ResourceType>>(false) {

            @Override
            protected PrismObject<ResourceType> load() {
                return loadResource(null);
            }
        };
        model = new LoadableModel<AccountContentSearchDto>(false) {

            @Override
            protected AccountContentSearchDto load() {
                return new AccountContentSearchDto();
            }
        };

        initLayout();
    }

    private void initLayout() {
        Form mainForm = new Form("mainForm");
        add(mainForm);

        OptionPanel option = new OptionPanel("option", createStringResource("pageContentAccounts.optionsTitle"),
                getPage(), false);
        option.setOutputMarkupId(true);
        mainForm.add(option);

        OptionItem item = new OptionItem("search", createStringResource("pageContentAccounts.search"));
        option.getBodyContainer().add(item);
        initSearch(item);

        OptionContent content = new OptionContent("optionContent");
        mainForm.add(content);
        initTable(content);
    }

    private void initSearch(OptionItem item) {
        TextField<String> search = new TextField<String>("searchText", new PropertyModel<String>(model, "searchText"));
        item.add(search);

        CheckBox nameCheck = new CheckBox("nameCheck", new PropertyModel<Boolean>(model, "name"));
        item.add(nameCheck);
        CheckBox fullNameCheck = new CheckBox("identifiersCheck", new PropertyModel<Boolean>(model, "identifiers"));
        item.add(fullNameCheck);

        AjaxSubmitLinkButton clearButton = new AjaxSubmitLinkButton("clearButton",
                createStringResource("pageContentAccounts.button.clearButton")) {

            @Override
            protected void onError(AjaxRequestTarget target, Form<?> form) {
                target.add(getFeedbackPanel());
            }

            @Override
            public void onSubmit(AjaxRequestTarget target, Form<?> form) {
                clearButtonPerformed(target);
            }
        };
        item.add(clearButton);

        AjaxSubmitLinkButton searchButton = new AjaxSubmitLinkButton("searchButton",
                createStringResource("pageContentAccounts.button.searchButton")) {

            @Override
            protected void onError(AjaxRequestTarget target, Form<?> form) {
                target.add(getFeedbackPanel());
            }

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                searchPerformed(target);
            }
        };
        item.add(searchButton);
    }

    private void initTable(OptionContent content) {
        List<IColumn> columns = initColumns();
        TablePanel table = new TablePanel("table", new AccountContentDataProvider(this,
                new PropertyModel<String>(resourceModel, "oid"), createObjectClassModel()), columns);
        table.setOutputMarkupId(true);
        content.getBodyContainer().add(table);
    }

    private List<IColumn> initColumns() {
        List<IColumn> columns = new ArrayList<IColumn>();

        IColumn column = new LinkColumn<SelectableBean<AccountContentDto>>(
                createStringResource("pageContentAccounts.name"), "value.accountName") {

            @Override
            public void onClick(AjaxRequestTarget target, IModel<SelectableBean<AccountContentDto>> rowModel) {
                AccountContentDto dto = rowModel.getObject().getValue();
                accountDetailsPerformed(target, dto.getAccountName(), dto.getAccountOid());
            }
        };
        columns.add(column);

        column = new AbstractColumn<SelectableBean<AccountContentDto>>(
                createStringResource("pageContentAccounts.identifiers")) {

            @Override
            public void populateItem(Item<ICellPopulator<SelectableBean<AccountContentDto>>> cellItem,
                                     String componentId, IModel<SelectableBean<AccountContentDto>> rowModel) {

                AccountContentDto dto = rowModel.getObject().getValue();
                List values = new ArrayList();
                for (ResourceAttribute<?> attr : dto.getIdentifiers()) {
                    values.add(attr.getName().getLocalPart() + ": " + attr.getRealValue());
                }
                cellItem.add(new Label(componentId, new Model<String>(StringUtils.join(values, ", "))));
            }
        };
        columns.add(column);

        column = new EnumPropertyColumn(createStringResource("pageContentAccounts.situation"), "value.situation") {

            @Override
            protected String translate(Enum en) {
                return createStringResource(en).getString();
            }
        };
        columns.add(column);

        column = new LinkColumn<SelectableBean<AccountContentDto>>(createStringResource("pageContentAccounts.owner")) {

            @Override
            protected IModel<String> createLinkModel(final IModel<SelectableBean<AccountContentDto>> rowModel) {
                return new AbstractReadOnlyModel<String>() {

                    @Override
                    public String getObject() {
                        AccountContentDto dto = rowModel.getObject().getValue();
                        StringBuilder owner = new StringBuilder();
                        if (StringUtils.isNotEmpty(dto.getOwnerName())) {
                            owner.append(dto.getOwnerName());
                            owner.append(" (");
                            owner.append(dto.getOwnerOid());
                            owner.append(")");
                        } else {
                            if (StringUtils.isNotEmpty(dto.getOwnerOid())) {
                                owner.append(dto.getOwnerOid());
                            }
                        }
                        return owner.toString();
                    }
                };
            }

            @Override
            public void onClick(AjaxRequestTarget target, IModel<SelectableBean<AccountContentDto>> rowModel) {
                AccountContentDto dto = rowModel.getObject().getValue();

                ownerDetailsPerformed(target, dto.getOwnerName(), dto.getOwnerOid());
            }
        };
        columns.add(column);

        column = new ButtonColumn<SelectableBean<AccountContentDto>>(new Model<String>(),
                createStringResource("pageContentAccounts.button.changeOwner")) {

            @Override
            public void onClick(AjaxRequestTarget target, IModel<SelectableBean<AccountContentDto>> rowModel) {
                changeOwnerPerformed(target, rowModel);
            }
        };
        columns.add(column);

        return columns;
    }

    @Override
    protected IModel<String> createPageTitleModel() {
        return new LoadableModel<String>(false) {

            @Override
            protected String load() {
                String name = WebMiscUtil.getName(resourceModel.getObject());
                return new StringResourceModel("page.title", PageContentAccounts.this, null, null, name).getString();
            }
        };
    }

    private void ownerDetailsPerformed(AjaxRequestTarget target, String ownerName, String ownerOid) {
        if (StringUtils.isEmpty(ownerOid)) {
            error(getString("pageContentAccounts.message.cantShowUserDetails", ownerName, ownerOid));
            target.add(getFeedbackPanel());
            return;
        }

        PageParameters parameters = new PageParameters();
        parameters.add(PageUser.PARAM_USER_ID, ownerOid);
        setResponsePage(PageUser.class, parameters);
    }

    private void changeOwnerPerformed(AjaxRequestTarget target, IModel<SelectableBean<AccountContentDto>> rowModel) {
        //todo implement
    }

    private void clearButtonPerformed(AjaxRequestTarget target) {
        model.reset();

        target.add(get("mainForm:option"));
        searchPerformed(target);
    }

    private TablePanel getTable() {
        OptionContent content = (OptionContent) get("mainForm:optionContent");
        return (TablePanel) content.getBodyContainer().get("table");
    }

    private void searchPerformed(AjaxRequestTarget target) {
        QueryType query = createQuery();

        TablePanel panel = getTable();
        DataTable table = panel.getDataTable();
        AccountContentDataProvider provider = (AccountContentDataProvider) table.getDataProvider();
        provider.setQuery(query);
        table.setCurrentPage(0);

        target.add(panel);
        target.add(getFeedbackPanel());
    }

    private QueryType createQuery() {
        AccountContentSearchDto dto = model.getObject();
        if (StringUtils.isEmpty(dto.getSearchText())) {
            return null;
        }

        try {
            QueryType query = new QueryType();
            Document document = DOMUtil.getDocument();

            List<Element> conditions = new ArrayList<Element>();
            if (dto.isIdentifiers()) {
                ObjectClassComplexTypeDefinition def = getAccountDefinition();
                List<ResourceAttributeDefinition> identifiers = new ArrayList<ResourceAttributeDefinition>();
                if (def.getIdentifiers() != null) {
                    identifiers.addAll(def.getIdentifiers());
                }
//                if (def.getSecondaryIdentifiers() != null) {
//                    identifiers.addAll(def.getIdentifiers());
//                }
                XPathHolder attributes = new XPathHolder(Arrays.asList(new XPathSegment(SchemaConstants.I_ATTRIBUTES)));
                for (ResourceAttributeDefinition attrDef : identifiers) {
                    conditions.add(QueryUtil.createSubstringFilter(document, attributes, attrDef.getName(), dto.getSearchText()));
                }
            }

            if (dto.isName()) {
                conditions.add(QueryUtil.createSubstringFilter(document, null, ObjectType.F_NAME, dto.getSearchText()));
            }

            if (!conditions.isEmpty()) {
                query = new QueryType();
                if (conditions.size() > 1) {
                    query.setFilter(QueryUtil.createOrFilter(document, conditions.toArray(new Element[conditions.size()])));
                } else {
                    query.setFilter(conditions.get(0));
                }
            }

            return query;
        } catch (Exception ex) {
            error(getString("pageUsers.message.queryError") + " " + ex.getMessage());
            LoggingUtils.logException(LOGGER, "Couldn't create query filter.", ex);
        }

        return null;
    }

    private IModel<QName> createObjectClassModel() {
        return new LoadableModel<QName>(false) {

            @Override
            protected QName load() {
                try {
                    return getObjectClassDefinition();
                } catch (Exception ex) {
                    throw new SystemException(ex.getMessage(), ex);
                }
            }
        };
    }

    private QName getObjectClassDefinition() throws SchemaException {
        ObjectClassComplexTypeDefinition def = getAccountDefinition();
        return def != null ? def.getTypeName() : null;
    }

    private ObjectClassComplexTypeDefinition getAccountDefinition() throws SchemaException {
        MidPointApplication application = (MidPointApplication) getApplication();
        PrismObject<ResourceType> resource = resourceModel.getObject();
        ResourceSchema resourceSchema = RefinedResourceSchema.getResourceSchema(resource, application.getPrismContext());
        Collection<ObjectClassComplexTypeDefinition> list = resourceSchema.getObjectClassDefinitions();
        if (list != null) {
            for (ObjectClassComplexTypeDefinition def : list) {
                if (def.isDefaultAccountType()) {
                    return def;
                }
            }
        }

        return null;
    }

    private void accountDetailsPerformed(AjaxRequestTarget target, String accountName, String accountOid) {
        if (StringUtils.isEmpty(accountOid)) {
            error(getString("pageContentAccounts.message.cantShowAccountDetails", accountName, accountOid));
            target.add(getFeedbackPanel());
            return;
        }

        PageParameters parameters = new PageParameters();
        parameters.add(PageAccount.PARAM_ACCOUNT_ID, accountOid);
        setResponsePage(PageAccount.class, parameters);
    }
}
