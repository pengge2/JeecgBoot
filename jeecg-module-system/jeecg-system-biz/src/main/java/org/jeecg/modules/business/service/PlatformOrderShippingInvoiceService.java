package org.jeecg.modules.business.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.business.controller.UserException;
import org.jeecg.modules.business.domain.excel.SheetManager;
import org.jeecg.modules.business.domain.shippingInvoice.CompleteInvoice;
import org.jeecg.modules.business.domain.shippingInvoice.ShippingInvoice;
import org.jeecg.modules.business.domain.shippingInvoice.ShippingInvoiceFactory;
import org.jeecg.modules.business.entity.PlatformOrder;
import org.jeecg.modules.business.entity.SavRefundWithDetail;
import org.jeecg.modules.business.mapper.*;
import org.jeecg.modules.business.vo.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class PlatformOrderShippingInvoiceService {

    @Autowired
    ShippingInvoiceMapper shippingInvoiceMapper;
    @Autowired
    PlatformOrderMapper platformOrderMapper;
    @Autowired
    ClientMapper clientMapper;
    @Autowired
    ShopMapper shopMapper;
    @Autowired
    LogisticChannelPriceMapper logisticChannelPriceMapper;
    @Autowired
    LogisticChannelMapper logisticChannelMapper;
    @Autowired
    IPlatformOrderContentService platformOrderContentService;
    @Autowired
    ISkuDeclaredValueService skuDeclaredValueService;
    @Autowired
    FactureDetailMapper factureDetailMapper;
    @Autowired
    IPlatformOrderService platformOrderService;
    @Autowired
    private IShopService shopService;
    @Autowired
    CountryService countryService;
    @Autowired
    IPurchaseOrderService purchaseOrderService;
    @Autowired
    PurchaseOrderContentMapper purchaseOrderContentMapper;
    @Autowired
    SkuPromotionHistoryMapper skuPromotionHistoryMapper;
    @Autowired
    ExchangeRatesMapper exchangeRatesMapper;
    @Autowired
    ISavRefundWithDetailService savRefundWithDetailService;
    @Autowired
    ISavRefundService savRefundService;

    @Value("${jeecg.path.shippingTemplatePath_EU}")
    private String SHIPPING_INVOICE_TEMPLATE_EU;

    @Value("${jeecg.path.shippingTemplatePath_US}")
    private String SHIPPING_INVOICE_TEMPLATE_US;

    @Value("${jeecg.path.completeTemplatePath_EU}")
    private String COMPLETE_INVOICE_TEMPLATE_EU;

    @Value("${jeecg.path.completeTemplatePath_US}")
    private String COMPLETE_INVOICE_TEMPLATE_US;

    @Value("${jeecg.path.shippingInvoiceDir}")
    private String INVOICE_DIR;

    @Value("${jeecg.path.shippingInvoiceDetailDir}")
    private String INVOICE_DETAIL_DIR;

    private final static String[] DETAILS_TITLES = {
            "Boutique",
            "N° de Mabang",
            "N° de commande",
            "N° de suivi",
            "Date de commande",
            "Date d'expédition",
            "Nom de client",
            "Pays",
            "Code postal",
            "SKU",
            "Nom produits",
            "Quantité",
            "Frais d'achat",
            "Frais de FRET",
            "Frais de livraison",
            "Frais de service",
            "Frais de préparation",
            "Frais de matériel d'emballage",
            "TVA",
            "N° de facture"
    };
    private final static String[] SAV_TITLES = {
            "Boutique",
            "N° de Mabang",
            "N° de commande",
            "Date du remboursement",
            "Montant d'achat remboursé",
            "Montant de livraison remboursé",
            "Montant total du remboursement",
            "N° de facture"
    };

    public Period getValidPeriod(List<String> shopIDs) {
        Date begin = platformOrderMapper.findEarliestUninvoicedPlatformOrder(shopIDs);
        Date end = platformOrderMapper.findLatestUninvoicedPlatformOrder(shopIDs);
        return new Period(begin, end);
    }
    public Period getValidOrderTimePeriod(List<String> shopIDs, List<Integer> erpStatuses) {
        Date begin = platformOrderMapper.findEarliestUninvoicedPlatformOrderTime(shopIDs, erpStatuses);
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        ZoneId paris = ZoneId.of("Europe/Paris");
        LocalDateTime ldt = LocalDateTime.ofInstant(begin.toInstant(), shanghai);
        Date beginZoned = Date.from(ldt.atZone(paris).toInstant());
        Date end = platformOrderMapper.findLatestUninvoicedPlatformOrderTime(shopIDs, erpStatuses);
        ldt = LocalDateTime.ofInstant(end.toInstant(), shanghai);
        Date endZoned = Date.from(ldt.atZone(paris).toInstant());
        return new Period(beginZoned, endZoned);
    }
    public List<String> getShippingOrderIdBetweenDate(List<String> shops, String start, String end, List<String> wareHouses) {
        List<PlatformOrder> orders = platformOrderMapper.fetchUninvoicedShippedOrderIDInShops( start, end, shops, wareHouses);
        return orders.stream().map(PlatformOrder::getId).collect(Collectors.toList());
    }
    /**
     * Make an invoice based on parameters.
     *
     * @param param the parameters to make the invoice
     * @return identifiant name of the invoice, can be used to in {@code getInvoiceBinary}.
     * @throws UserException  exception due to error of user input, message will contain detail
     * @throws ParseException exception because of format of "start" and "end" date does not follow
     *                        pattern: "yyyy-MM-dd"
     * @throws IOException    exception related to invoice file IO.
     */
    @Transactional
    public InvoiceMetaData makeInvoice(ShippingInvoiceParam param) throws UserException, ParseException, IOException {
        // Creates factory
        ShippingInvoiceFactory factory = new ShippingInvoiceFactory(
                platformOrderService, clientMapper, shopMapper, logisticChannelMapper, logisticChannelPriceMapper,
                platformOrderContentService, skuDeclaredValueService, countryService, exchangeRatesMapper,
                purchaseOrderService, purchaseOrderContentMapper, skuPromotionHistoryMapper, savRefundService, savRefundWithDetailService);
        String username = ((LoginUser) SecurityUtils.getSubject().getPrincipal()).getUsername();
        // Creates invoice by factory
        ShippingInvoice invoice = factory.createInvoice(param.clientID(),
                param.shopIDs(),
                param.start(),
                param.end(),
                param.getErpStatuses(),
                param.getWarehouses()
        );
        // Chooses invoice template based on client's preference on currency
        return getInvoiceMetaData(username, invoice);
    }

    /**
     * Make a pre-shipping invoice for specified orders
     *
     * @param param the parameters to make the invoice
     * @return name of the invoice, can be used to in {@code getInvoiceBinary}.
     * @throws UserException  exception due to error of user input, message will contain detail
     * @throws ParseException exception because of format of "start" and "end" date does not follow
     *                        pattern: "yyyy-MM-dd"
     * @throws IOException    exception related to invoice file IO.
     */
    @Transactional
    public InvoiceMetaData makeInvoice(ShippingInvoiceOrderParam param) throws UserException, ParseException, IOException {
        // Creates factory
        ShippingInvoiceFactory factory = new ShippingInvoiceFactory(
                platformOrderService, clientMapper, shopMapper, logisticChannelMapper, logisticChannelPriceMapper,
                platformOrderContentService, skuDeclaredValueService, countryService, exchangeRatesMapper,
                purchaseOrderService, purchaseOrderContentMapper, skuPromotionHistoryMapper, savRefundService, savRefundWithDetailService);
        String username = ((LoginUser) SecurityUtils.getSubject().getPrincipal()).getUsername();
        // Creates invoice by factory
        ShippingInvoice invoice = factory.createShippingInvoice(param.clientID(), param.orderIds(), param.getType(), param.getStart(), param.getEnd());
        return getInvoiceMetaData(username, invoice);
    }

    /**
     * Make a complete shipping invoice (purchase + shipping) invoice for specified orders and order statuses
     *
     * @param param the parameters to make the invoice
     * @return name of the invoice, can be used to in {@code getInvoiceBinary}.
     * @throws UserException  exception due to error of user input, message will contain detail
     * @throws ParseException exception because of format of "start" and "end" date does not follow
     *                        pattern: "yyyy-MM-dd"
     * @throws IOException    exception related to invoice file IO.
     */
    @Transactional
    public InvoiceMetaData makeCompleteInvoice(ShippingInvoiceOrderParam param) throws UserException, ParseException, IOException {
        // Creates factory
        ShippingInvoiceFactory factory = new ShippingInvoiceFactory(
                platformOrderService, clientMapper, shopMapper, logisticChannelMapper, logisticChannelPriceMapper,
                platformOrderContentService, skuDeclaredValueService, countryService, exchangeRatesMapper,
                purchaseOrderService, purchaseOrderContentMapper, skuPromotionHistoryMapper, savRefundService, savRefundWithDetailService);
        String username = ((LoginUser) SecurityUtils.getSubject().getPrincipal()).getUsername();
        // Creates invoice by factory
        CompleteInvoice invoice = factory.createCompleteShippingInvoice(username, param.clientID(), param.orderIds(), param.getType(), param.getStart(), param.getEnd());
        return getInvoiceMetaData(username, invoice);
    }

    /**
     *  Make a complete post-shipping (purchase + shipping)
     * @param param clientID, shopIPs[], startDate, endDate
     * @param method "post" = postShipping, "pre" = preShipping, "all" = all shipping methods
     * @return name of the invoice, can be used to in {@code getInvoiceBinary}
     * @throws UserException
     * @throws ParseException
     * @throws IOException
     */
    @Transactional
    public InvoiceMetaData makeCompleteInvoicePostShipping(ShippingInvoiceParam param, String method) throws UserException, ParseException, IOException {
        // Creates factory
        ShippingInvoiceFactory factory = new ShippingInvoiceFactory(
                platformOrderService, clientMapper, shopMapper, logisticChannelMapper, logisticChannelPriceMapper,
                platformOrderContentService, skuDeclaredValueService, countryService, exchangeRatesMapper,
                purchaseOrderService, purchaseOrderContentMapper, skuPromotionHistoryMapper, savRefundService, savRefundWithDetailService);
        String username = ((LoginUser) SecurityUtils.getSubject().getPrincipal()).getUsername();
        List<PlatformOrder> platformOrderList;
        if(method.equals("post")) {
            //On récupère les commandes entre 2 dates d'expédition avec un status 3
            platformOrderList = platformOrderMapper.fetchUninvoicedShippedOrderIDInShops(param.getStart(), param.getEnd(), param.shopIDs(), param.getWarehouses());
        } else {
            // On récupère les commandes entre 2 dates de commandes avec un status (1,2) ou (1,2,3)
            platformOrderList = platformOrderMapper.fetchUninvoicedShippedOrderIDInShopsAndOrderTime(param.getStart(), param.getEnd(), param.shopIDs(), param.getErpStatuses(), param.getWarehouses());
        }
        // on récupère seulement les ID des commandes
        List<String> orderIds = platformOrderList.stream().map(PlatformOrder::getId).collect(Collectors.toList());
        // Creates invoice by factory
        CompleteInvoice invoice = factory.createCompleteShippingInvoice(username, param.clientID(), orderIds, method, param.getStart(), param.getEnd());
        return getInvoiceMetaData(username, invoice);
    }
    @NotNull
    private InvoiceMetaData getInvoiceMetaData(String username, ShippingInvoice invoice) throws IOException {
        // Chooses invoice template based on client's preference on currency
        Path src;
        if (invoice instanceof CompleteInvoice) {
            if (invoice.client().getCurrency().equals("USD")) {
                src = Paths.get(COMPLETE_INVOICE_TEMPLATE_US);
            } else {
                src = Paths.get(COMPLETE_INVOICE_TEMPLATE_EU);
            }
        } else {
            if (invoice.client().getCurrency().equals("USD")) {
                src = Paths.get(SHIPPING_INVOICE_TEMPLATE_US);
            } else {
                src = Paths.get(SHIPPING_INVOICE_TEMPLATE_EU);
            }
        }

        // Writes invoice content to a new excel file
        String filename = "Invoice N°" + invoice.code() + " (" + invoice.client().getInvoiceEntity() + ").xlsx";
        Path out = Paths.get(INVOICE_DIR, filename);
        Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING);
        invoice.toExcelFile(out);
        // save to DB
        org.jeecg.modules.business.entity.ShippingInvoice shippingInvoiceEntity = org.jeecg.modules.business.entity.ShippingInvoice.of(
                username,
                invoice.client().getId(),
                invoice.code(),
                invoice.getTotalAmount(),
                invoice.reducedAmount(),
                invoice.paidAmount()
        );
        shippingInvoiceMapper.insert(shippingInvoiceEntity);
        return new InvoiceMetaData(filename, invoice.code(), invoice.client().getInternalCode(), invoice.client().getInvoiceEntity(), "");
    }

    /**
     * Get an estimation of all shipped orders
     *
     * @return List of shipping fees estimations.
     * @param errorMessages List of error messages to be filled
     */
    public List<ShippingFeesEstimation> getShippingFeesEstimation(List<String> errorMessages) {
        // Creates factory
        ShippingInvoiceFactory factory = new ShippingInvoiceFactory(
                platformOrderService, clientMapper, shopMapper, logisticChannelMapper, logisticChannelPriceMapper,
                platformOrderContentService, skuDeclaredValueService, countryService, exchangeRatesMapper,
                purchaseOrderService, purchaseOrderContentMapper, skuPromotionHistoryMapper, savRefundService, savRefundWithDetailService);
        return factory.getEstimations(errorMessages);
    }

    /**
     * Get an estimation of selected orders that are yet to be shipped
     *
     * @return List of shipping fees estimations.
     * @param errorMessages List of error messages to be filled
     */
    public List<ShippingFeesEstimation> getShippingFeesEstimation(String clientId, List<String> orderIds,List<String> errorMessages) {
        // Creates factory
        ShippingInvoiceFactory factory = new ShippingInvoiceFactory(
                platformOrderService, clientMapper, shopMapper, logisticChannelMapper, logisticChannelPriceMapper,
                platformOrderContentService, skuDeclaredValueService, countryService, exchangeRatesMapper,
                purchaseOrderService, purchaseOrderContentMapper, skuPromotionHistoryMapper, savRefundService, savRefundWithDetailService);
        return factory.getEstimations(clientId, orderIds, errorMessages);
    }

    /**
     * Returns byte stream of a invoice file
     *
     * @param filename identifiant name of the invoice file
     * @return byte array of the file
     * @throws IOException error when reading file
     */
    public byte[] getInvoiceBinary(String filename) throws IOException {
        Path out = Paths.get(INVOICE_DIR, filename);
        return Files.readAllBytes(out);
    }

    public List<FactureDetail> getInvoiceDetail(String invoiceNumber) {

        QueryWrapper<FactureDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("`N° de facture`", invoiceNumber);

        return factureDetailMapper.selectList(queryWrapper);
    }

    public byte[] exportToExcel(List<FactureDetail> details, List<SavRefundWithDetail> refunds, String invoiceNumber, String invoiceEntity, String internalCode) throws IOException {
        SheetManager sheetManager = SheetManager.createXLSX();
        sheetManager.startDetailsSheet();
        for (String title : DETAILS_TITLES) {
            sheetManager.write(title);
            sheetManager.nextCol();
        }
        sheetManager.moveCol(0);
        sheetManager.nextRow();

        for (FactureDetail detail : details) {
            sheetManager.write(detail.getBoutique());
            sheetManager.nextCol();
            sheetManager.write(detail.getMabangNum());
            sheetManager.nextCol();
            sheetManager.write(detail.getCommandeNum());
            sheetManager.nextCol();
            sheetManager.write(detail.getSuiviNum());
            sheetManager.nextCol();
            sheetManager.write(detail.getCommandeDate());
            sheetManager.nextCol();
            sheetManager.write(detail.getExpeditionDate());
            sheetManager.nextCol();
            sheetManager.write(detail.getClientName());
            sheetManager.nextCol();
            sheetManager.write(detail.getCountry());
            sheetManager.nextCol();
            sheetManager.write(detail.getPostalCode());
            sheetManager.nextCol();
            sheetManager.write(detail.getSku());
            sheetManager.nextCol();
            sheetManager.write(detail.getProductName());
            sheetManager.nextCol();
            sheetManager.write(detail.getQuantity());
            sheetManager.nextCol();
            sheetManager.write(detail.getPurchaseFee());
            sheetManager.nextCol();
            sheetManager.write(detail.getFretFee());
            sheetManager.nextCol();
            sheetManager.write(detail.getLivraisonFee());
            sheetManager.nextCol();
            sheetManager.write(detail.getServiceFee());
            sheetManager.nextCol();
            sheetManager.write(detail.getPickingFee());
            sheetManager.nextCol();
            sheetManager.write(detail.getPackagingMaterialFee());
            sheetManager.nextCol();
            sheetManager.write(detail.getTVA());
            sheetManager.nextCol();
            sheetManager.write(detail.getFactureNum());
            sheetManager.moveCol(0);
            sheetManager.nextRow();
        }
        sheetManager.startSavSheet();
        for (String title : SAV_TITLES) {
            sheetManager.write(title);
            sheetManager.nextCol();
        }
        sheetManager.moveCol(0);
        sheetManager.nextRow();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (SavRefundWithDetail refund : refunds) {
            sheetManager.write(refund.getShopName());
            sheetManager.nextCol();
            sheetManager.write(refund.getMabangId());
            sheetManager.nextCol();
            sheetManager.write(refund.getPlatformOrderNumber());
            sheetManager.nextCol();
            sheetManager.write(sdf.format(refund.getRefundDate()));
            sheetManager.nextCol();
            sheetManager.write(refund.getPurchaseRefundAmount());
            sheetManager.nextCol();
            sheetManager.write(refund.getShippingFee()
                    .add(refund.getFretFee())
                    .add(refund.getVat())
                    .add(refund.getServiceFee()));
            sheetManager.nextCol();
            sheetManager.write(refund.getTotalRefundAmount());
            sheetManager.nextCol();
            sheetManager.write(refund.getInvoiceNumber());
            sheetManager.moveCol(0);
            sheetManager.nextRow();
        }

        Path target = Paths.get(INVOICE_DETAIL_DIR, internalCode + "_(" + invoiceEntity + ")_" + invoiceNumber + "_Détail_calcul_de_facture.xlsx");
        int i = 2;
        while (Files.exists(target)) {
            target = Paths.get(INVOICE_DETAIL_DIR, internalCode + "_(" + invoiceEntity + ")_" + invoiceNumber + "_Détail_calcul_de_facture_(" + i + ").xlsx");
            i++;
        }
        Files.createFile(target);
        sheetManager.export(target);
        sheetManager.getWorkbook().close();
        System.gc();
        return Files.readAllBytes(target);
    }


    /**
     * make shipping invoice by client and type (shipping or complete)
     * @param clientIds list of client codes
     * @param invoiceType shipping invoice or complete invoice
     * @return list of filename (invoices and details)
     */
    @Transactional
    public List<InvoiceMetaData> breakdownInvoiceClientByType(List<String> clientIds, int invoiceType) {
        Map<String, List<String>> clientShopIDsMap = new HashMap<>();
        List<InvoiceMetaData> invoiceList = new ArrayList<>();
        for(String id: clientIds) {
            clientShopIDsMap.put(id, shopService.listIdByClient(id));
        }
        for(Map.Entry<String, List<String>> entry: clientShopIDsMap.entrySet()) {
            Period period = getValidPeriod(entry.getValue());
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(period.start());
            String start = calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH)+1 < 10 ? "0" : "") + (calendar.get(Calendar.MONTH)+1) + "-" + (calendar.get(Calendar.DAY_OF_MONTH) < 10 ? "0" : "") + (calendar.get(Calendar.DAY_OF_MONTH));
            calendar.setTime(period.end());
            String end = calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH)+1 < 10 ? "0" : "") + (calendar.get(Calendar.MONTH)+1) + "-" + (calendar.get(Calendar.DAY_OF_MONTH)+1 < 10 ? "0" : "") + (calendar.get(Calendar.DAY_OF_MONTH)+1);
            log.info( "Invoicing : " + (invoiceType == 0 ? "Shipping Invoice" : "Complete Shipping Invoice") +
                    "\nclient : " + entry.getKey() +
                    "\nbetween dates : [" + start + "] --- [" + end + "]");
            try {
                ShippingInvoiceParam param = new ShippingInvoiceParam(entry.getKey(), entry.getValue(), start, end, Collections.singletonList(3), Arrays.asList("0", "1"));
                InvoiceMetaData metaData;
                if(invoiceType == 0)
                    metaData = makeInvoice(param);
                else
                    metaData = makeCompleteInvoicePostShipping(param, "post");
                invoiceList.add(metaData);
            } catch (UserException | IOException | ParseException e) {
                invoiceList.add(new InvoiceMetaData("", "error", "", entry.getKey(), e.getMessage()));
                log.error(e.getMessage());
            }
            System.gc();
        }
        return invoiceList;
    }

    @Transactional
    public String zipInvoices(List<String> invoiceList) throws IOException {
        log.info("Zipping Invoices ...");
        String username = ((LoginUser) SecurityUtils.getSubject().getPrincipal()).getUsername();
        String now = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date());
        String zipFilename = Paths.get(INVOICE_DIR).getParent().toAbsolutePath() + "/breakdownInvoices_" + username + "_" + now +".zip";
        final FileOutputStream fos = new FileOutputStream(zipFilename);
        ZipOutputStream zipOut = new ZipOutputStream(fos);

        for (String srcFile : invoiceList) {
            File fileToZip = new File(srcFile);
            FileInputStream fis = new FileInputStream(fileToZip);
            ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
        }

        zipOut.close();
        fos.close();
        log.info("Zipping done ...");
        return zipFilename;
    }
}
