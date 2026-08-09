---
name: 6502 Instruction Reference
description: Complete 6502 instruction set with opcodes, addressing modes, cycle counts, and flags
type: reference
---

# 6502 Instruction Reference

Source: https://www.nesdev.org/obelisk-6502-guide/reference.html

> (+1) = add 1 cycle if page boundary crossed
> (+1/+2) for branches = +1 if branch taken, +2 if to new page

## Flag Reference

| Flag | Meaning |
|------|---------|
| C | Carry |
| Z | Zero |
| I | Interrupt Disable |
| D | Decimal Mode (ignored on NES) |
| B | Break Command |
| V | Overflow |
| N | Negative |

---

## ADC — Add with Carry
**Operation:** `A,Z,C,N = A+M+C`
**Flags:** C, Z, V, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $69 | 2 | 2 |
| Zero Page | $65 | 2 | 3 |
| Zero Page,X | $75 | 2 | 4 |
| Absolute | $6D | 3 | 4 |
| Absolute,X | $7D | 3 | 4 (+1) |
| Absolute,Y | $79 | 3 | 4 (+1) |
| (Indirect,X) | $61 | 2 | 6 |
| (Indirect),Y | $71 | 2 | 5 (+1) |

## AND — Logical AND
**Operation:** `A,Z,N = A&M`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $29 | 2 | 2 |
| Zero Page | $25 | 2 | 3 |
| Zero Page,X | $35 | 2 | 4 |
| Absolute | $2D | 3 | 4 |
| Absolute,X | $3D | 3 | 4 (+1) |
| Absolute,Y | $39 | 3 | 4 (+1) |
| (Indirect,X) | $21 | 2 | 6 |
| (Indirect),Y | $31 | 2 | 5 (+1) |

## ASL — Arithmetic Shift Left
**Operation:** `A,Z,C,N = M*2` or `M,Z,C,N = M*2`
**Flags:** C (old bit 7), Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Accumulator | $0A | 1 | 2 |
| Zero Page | $06 | 2 | 5 |
| Zero Page,X | $16 | 2 | 6 |
| Absolute | $0E | 3 | 6 |
| Absolute,X | $1E | 3 | 7 |

## BCC — Branch if Carry Clear
**Operation:** Branch if C = 0
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Relative | $90 | 2 | 2 (+1/+2) |

## BCS — Branch if Carry Set
**Operation:** Branch if C = 1
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Relative | $B0 | 2 | 2 (+1/+2) |

## BEQ — Branch if Equal
**Operation:** Branch if Z = 1
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Relative | $F0 | 2 | 2 (+1/+2) |

## BIT — Bit Test
**Operation:** `A & M, N = M7, V = M6`
**Flags:** Z (result zero), V (bit 6 of M), N (bit 7 of M)

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Zero Page | $24 | 2 | 3 |
| Absolute | $2C | 3 | 4 |

## BMI — Branch if Minus
**Operation:** Branch if N = 1
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Relative | $30 | 2 | 2 (+1/+2) |

## BNE — Branch if Not Equal
**Operation:** Branch if Z = 0
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Relative | $D0 | 2 | 2 (+1/+2) |

## BPL — Branch if Positive
**Operation:** Branch if N = 0
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Relative | $10 | 2 | 2 (+1/+2) |

## BRK — Force Interrupt
**Operation:** Force interrupt; push PC+2 and flags to stack; load IRQ vector
**Flags:** B = 1

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $00 | 1 | 7 |

## BVC — Branch if Overflow Clear
**Operation:** Branch if V = 0
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Relative | $50 | 2 | 2 (+1/+2) |

## BVS — Branch if Overflow Set
**Operation:** Branch if V = 1
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Relative | $70 | 2 | 2 (+1/+2) |

## CLC — Clear Carry Flag
**Operation:** `C = 0`
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $18 | 1 | 2 |

## CLD — Clear Decimal Mode
**Operation:** `D = 0`
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $D8 | 1 | 2 |

## CLI — Clear Interrupt Disable
**Operation:** `I = 0`
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $58 | 1 | 2 |

## CLV — Clear Overflow Flag
**Operation:** `V = 0`
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $B8 | 1 | 2 |

## CMP — Compare
**Operation:** `Z,C,N = A-M`
**Flags:** C (A≥M), Z (A=M), N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $C9 | 2 | 2 |
| Zero Page | $C5 | 2 | 3 |
| Zero Page,X | $D5 | 2 | 4 |
| Absolute | $CD | 3 | 4 |
| Absolute,X | $DD | 3 | 4 (+1) |
| Absolute,Y | $D9 | 3 | 4 (+1) |
| (Indirect,X) | $C1 | 2 | 6 |
| (Indirect),Y | $D1 | 2 | 5 (+1) |

## CPX — Compare X Register
**Operation:** `Z,C,N = X-M`
**Flags:** C (X≥M), Z (X=M), N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $E0 | 2 | 2 |
| Zero Page | $E4 | 2 | 3 |
| Absolute | $EC | 3 | 4 |

## CPY — Compare Y Register
**Operation:** `Z,C,N = Y-M`
**Flags:** C (Y≥M), Z (Y=M), N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $C0 | 2 | 2 |
| Zero Page | $C4 | 2 | 3 |
| Absolute | $CC | 3 | 4 |

## DEC — Decrement Memory
**Operation:** `M,Z,N = M-1`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Zero Page | $C6 | 2 | 5 |
| Zero Page,X | $D6 | 2 | 6 |
| Absolute | $CE | 3 | 6 |
| Absolute,X | $DE | 3 | 7 |

## DEX — Decrement X Register
**Operation:** `X,Z,N = X-1`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $CA | 1 | 2 |

## DEY — Decrement Y Register
**Operation:** `Y,Z,N = Y-1`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $88 | 1 | 2 |

## EOR — Exclusive OR
**Operation:** `A,Z,N = A^M`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $49 | 2 | 2 |
| Zero Page | $45 | 2 | 3 |
| Zero Page,X | $55 | 2 | 4 |
| Absolute | $4D | 3 | 4 |
| Absolute,X | $5D | 3 | 4 (+1) |
| Absolute,Y | $59 | 3 | 4 (+1) |
| (Indirect,X) | $41 | 2 | 6 |
| (Indirect),Y | $51 | 2 | 5 (+1) |

## INC — Increment Memory
**Operation:** `M,Z,N = M+1`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Zero Page | $E6 | 2 | 5 |
| Zero Page,X | $F6 | 2 | 6 |
| Absolute | $EE | 3 | 6 |
| Absolute,X | $FE | 3 | 7 |

## INX — Increment X Register
**Operation:** `X,Z,N = X+1`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $E8 | 1 | 2 |

## INY — Increment Y Register
**Operation:** `Y,Z,N = Y+1`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $C8 | 1 | 2 |

## JMP — Jump
**Operation:** Set PC to target address

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Absolute | $4C | 3 | 3 |
| Indirect | $6C | 3 | 5 |

> **Bug:** Indirect JMP wraps within the page. `JMP ($10FF)` reads low byte from $10FF and high byte from $1000 (not $1100).

## JSR — Jump to Subroutine
**Operation:** Push PC-1 to stack, set PC to target

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Absolute | $20 | 3 | 6 |

## LDA — Load Accumulator
**Operation:** `A,Z,N = M`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $A9 | 2 | 2 |
| Zero Page | $A5 | 2 | 3 |
| Zero Page,X | $B5 | 2 | 4 |
| Absolute | $AD | 3 | 4 |
| Absolute,X | $BD | 3 | 4 (+1) |
| Absolute,Y | $B9 | 3 | 4 (+1) |
| (Indirect,X) | $A1 | 2 | 6 |
| (Indirect),Y | $B1 | 2 | 5 (+1) |

## LDX — Load X Register
**Operation:** `X,Z,N = M`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $A2 | 2 | 2 |
| Zero Page | $A6 | 2 | 3 |
| Zero Page,Y | $B6 | 2 | 4 |
| Absolute | $AE | 3 | 4 |
| Absolute,Y | $BE | 3 | 4 (+1) |

## LDY — Load Y Register
**Operation:** `Y,Z,N = M`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $A0 | 2 | 2 |
| Zero Page | $A4 | 2 | 3 |
| Zero Page,X | $B4 | 2 | 4 |
| Absolute | $AC | 3 | 4 |
| Absolute,X | $BC | 3 | 4 (+1) |

## LSR — Logical Shift Right
**Operation:** `A,C,Z,N = A/2` or `M,C,Z,N = M/2`
**Flags:** C (old bit 0), Z, N (always 0)

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Accumulator | $4A | 1 | 2 |
| Zero Page | $46 | 2 | 5 |
| Zero Page,X | $56 | 2 | 6 |
| Absolute | $4E | 3 | 6 |
| Absolute,X | $5E | 3 | 7 |

## NOP — No Operation
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $EA | 1 | 2 |

## ORA — Logical Inclusive OR
**Operation:** `A,Z,N = A|M`
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $09 | 2 | 2 |
| Zero Page | $05 | 2 | 3 |
| Zero Page,X | $15 | 2 | 4 |
| Absolute | $0D | 3 | 4 |
| Absolute,X | $1D | 3 | 4 (+1) |
| Absolute,Y | $19 | 3 | 4 (+1) |
| (Indirect,X) | $01 | 2 | 6 |
| (Indirect),Y | $11 | 2 | 5 (+1) |

## PHA — Push Accumulator
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $48 | 1 | 3 |

## PHP — Push Processor Status
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $08 | 1 | 3 |

## PLA — Pull Accumulator
**Flags:** Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $68 | 1 | 4 |

## PLP — Pull Processor Status
**Flags:** All flags set from stack

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $28 | 1 | 4 |

## ROL — Rotate Left
**Operation:** Shift left; old bit 7 → C; old C → bit 0
**Flags:** C (old bit 7), Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Accumulator | $2A | 1 | 2 |
| Zero Page | $26 | 2 | 5 |
| Zero Page,X | $36 | 2 | 6 |
| Absolute | $2E | 3 | 6 |
| Absolute,X | $3E | 3 | 7 |

## ROR — Rotate Right
**Operation:** Shift right; old bit 0 → C; old C → bit 7
**Flags:** C (old bit 0), Z, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Accumulator | $6A | 1 | 2 |
| Zero Page | $66 | 2 | 5 |
| Zero Page,X | $76 | 2 | 6 |
| Absolute | $6E | 3 | 6 |
| Absolute,X | $7E | 3 | 7 |

## RTI — Return from Interrupt
**Operation:** Pull flags then PC from stack
**Flags:** All restored from stack

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $40 | 1 | 6 |

## RTS — Return from Subroutine
**Operation:** Pull PC from stack, increment PC

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $60 | 1 | 6 |

## SBC — Subtract with Carry
**Operation:** `A,Z,C,N = A-M-(1-C)`
**Flags:** C (clear if borrow), Z, V, N

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Immediate | $E9 | 2 | 2 |
| Zero Page | $E5 | 2 | 3 |
| Zero Page,X | $F5 | 2 | 4 |
| Absolute | $ED | 3 | 4 |
| Absolute,X | $FD | 3 | 4 (+1) |
| Absolute,Y | $F9 | 3 | 4 (+1) |
| (Indirect,X) | $E1 | 2 | 6 |
| (Indirect),Y | $F1 | 2 | 5 (+1) |

## SEC — Set Carry Flag
**Operation:** `C = 1`
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $38 | 1 | 2 |

## SED — Set Decimal Mode
**Operation:** `D = 1`
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $F8 | 1 | 2 |

## SEI — Set Interrupt Disable
**Operation:** `I = 1`
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $78 | 1 | 2 |

## STA — Store Accumulator
**Operation:** `M = A`

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Zero Page | $85 | 2 | 3 |
| Zero Page,X | $95 | 2 | 4 |
| Absolute | $8D | 3 | 4 |
| Absolute,X | $9D | 3 | 5 |
| Absolute,Y | $99 | 3 | 5 |
| (Indirect,X) | $81 | 2 | 6 |
| (Indirect),Y | $91 | 2 | 6 |

## STX — Store X Register
**Operation:** `M = X`

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Zero Page | $86 | 2 | 3 |
| Zero Page,Y | $96 | 2 | 4 |
| Absolute | $8E | 3 | 4 |

## STY — Store Y Register
**Operation:** `M = Y`

| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Zero Page | $84 | 2 | 3 |
| Zero Page,X | $94 | 2 | 4 |
| Absolute | $8C | 3 | 4 |

## TAX — Transfer Accumulator to X
**Operation:** `X = A`  **Flags:** Z, N
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $AA | 1 | 2 |

## TAY — Transfer Accumulator to Y
**Operation:** `Y = A`  **Flags:** Z, N
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $A8 | 1 | 2 |

## TSX — Transfer Stack Pointer to X
**Operation:** `X = S`  **Flags:** Z, N
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $BA | 1 | 2 |

## TXA — Transfer X to Accumulator
**Operation:** `A = X`  **Flags:** Z, N
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $8A | 1 | 2 |

## TXS — Transfer X to Stack Pointer
**Operation:** `S = X`  (no flags affected)
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $9A | 1 | 2 |

## TYA — Transfer Y to Accumulator
**Operation:** `A = Y`  **Flags:** Z, N
| Mode | Opcode | Bytes | Cycles |
|------|--------|-------|--------|
| Implied | $98 | 1 | 2 |
