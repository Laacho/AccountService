package com.banking.account.bankBranch.model;

/**
 * each branch of the bank needs a different code
 * example:
 * String sofiaCenterBranch  = "1001";
 * String sofiaAirportBranch = "1002";
 * String plovdivBranch      = "4001";
 * String varnaBranch        = "8001";
 */

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "bank_branches")
public class BankBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "branch_name", nullable = false, unique = true)
    private String branchName;

    @Column(unique = true, nullable = false)
    private String code;

    //the address of the office
    @Column(nullable = false)
    private String address;

    @Column(nullable = false, name = "contact_phone_number")
    private String contactPhoneNumber;


}
